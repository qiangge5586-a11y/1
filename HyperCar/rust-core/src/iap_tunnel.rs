//! DiPlay 0.3.11 AirPlay DataStream type-130 tunnel for outbound virtual iAP2.
use std::time::Duration;
use tokio::io::AsyncWriteExt;
use tokio::net::tcp::{OwnedReadHalf, OwnedWriteHalf};
use crate::airplay::SocketProvider;
use crate::bplist::{self, Value};
use crate::crypto::{chacha_seal, hkdf_sha512, nonce64};
use crate::rtsp::RtspChannel;

pub struct IapTunnelWriter {
    // Keep the read half alive for the lifetime of the TCP tunnel.
    _reader: OwnedReadHalf,
    writer: OwnedWriteHalf,
    key: [u8; 32],
    counter: u64,
}

fn random_u32() -> u32 {
    use rand_core::{OsRng, RngCore};
    OsRng.next_u32()
}

pub fn build_setup_body(seed: u32, connection_id: u32) -> Vec<u8> {
    let stream = Value::dict(vec![
        ("type", Value::int(130)),
        ("streamConnectionID", Value::int(connection_id as i64)),
        ("clientTypeUUID", Value::str("E9459FD0-BCAD-4C45-820F-1E72447EF2F2")),
        ("seed", Value::int(seed as i64)),
    ]);
    bplist::encode(&Value::dict(vec![("streams", Value::array(vec![stream]))]))
}

pub fn derive_key(shared_secret: &[u8; 32], seed: u32) -> [u8; 32] {
    let salt = format!("DataStream-Salt{seed}");
    let key = hkdf_sha512(
        shared_secret,
        salt.as_bytes(),
        b"DataStream-Output-Encryption-Key",
        32,
    );
    let mut out = [0u8; 32];
    if key.len() == 32 { out.copy_from_slice(&key); }
    out
}

pub fn parse_setup_response(body: &[u8], connection_id: u32) -> Result<u16, String> {
    let root = bplist::parse(body).map_err(|e| format!("type-130 SETUP plist parse: {e}"))?;
    let stream = root.get("streams").and_then(Value::as_array)
        .and_then(|v| v.iter().find(|s| s.get("type").and_then(Value::as_int) == Some(130)))
        .ok_or_else(|| "type-130 SETUP response missing stream".to_string())?;
    let stream_id = stream.get("streamID").and_then(Value::as_int)
        .ok_or_else(|| "type-130 SETUP response missing streamID".to_string())?;
    if stream_id != 1 {
        return Err(format!("type-130 streamID mismatch expected=1 got={stream_id}"));
    }
    if let Some(id) = stream.get("streamConnectionID").and_then(Value::as_int) {
        if id != connection_id as i64 {
            return Err(format!("type-130 streamConnectionID mismatch expected={connection_id} got={id}"));
        }
    }
    let port = stream.get("dataPort").and_then(Value::as_int)
        .and_then(|v| u16::try_from(v).ok()).filter(|v| *v != 0)
        .ok_or_else(|| "type-130 SETUP response missing valid dataPort".to_string())?;
    Ok(port)
}

pub fn build_comm_package(payload: &[u8]) -> Result<Vec<u8>, String> {
    let total = 32usize.checked_add(payload.len()).ok_or("comm package length overflow")?;
    if total > 4 * 1024 * 1024 { return Err(format!("comm package too large: {total}")); }
    let mut out = vec![0u8; total];
    out[0..4].copy_from_slice(&(total as u32).to_be_bytes());
    out[16..20].copy_from_slice(&0x636f6d6du32.to_be_bytes());
    out[32..].copy_from_slice(payload);
    Ok(out)
}

pub fn encrypt_frame(key: &[u8; 32], counter: u64, plaintext: &[u8]) -> Result<Vec<u8>, String> {
    let encrypted_len = plaintext.len().checked_add(16).ok_or("frame length overflow")?;
    let length = u16::try_from(encrypted_len).map_err(|_| "encrypted frame exceeds u16".to_string())?;
    let prefix = length.to_le_bytes();
    let encrypted = chacha_seal(key, &nonce64(counter), plaintext, &prefix);
    if encrypted.len() != encrypted_len { return Err("ChaCha20-Poly1305 encryption failed".into()); }
    let mut frame = Vec::with_capacity(2 + encrypted.len());
    frame.extend_from_slice(&prefix);
    frame.extend_from_slice(&encrypted);
    Ok(frame)
}

pub fn encode_comm_frames(key: &[u8; 32], first_counter: u64, payload: &[u8]) -> Result<Vec<Vec<u8>>, String> {
    let mut frames = Vec::new();
    let max_plain = (u16::MAX as usize) - 16;
    for (index, chunk) in payload.chunks(max_plain).enumerate() {
        frames.push(encrypt_frame(key, first_counter.checked_add(index as u64).ok_or("frame counter overflow")?, chunk)?);
    }
    Ok(frames)
}

impl IapTunnelWriter {
    pub async fn send(&mut self, payload: &[u8]) -> Result<(), String> {
        let package = build_comm_package(payload)?;
        let frames = encode_comm_frames(&self.key, self.counter, &package)?;
        for frame in frames {
            self.writer.write_all(&frame).await.map_err(|e| format!("type-130 tunnel write: {e}"))?;
            self.counter = self.counter.checked_add(1).ok_or("type-130 frame counter overflow")?;
        }
        self.writer.flush().await.map_err(|e| format!("type-130 tunnel flush: {e}"))?;
        Ok(())
    }
}

pub async fn connect(
    channel: &RtspChannel,
    car_ip: &str,
    shared_secret: &[u8; 32],
    factory: &dyn SocketProvider,
) -> Result<IapTunnelWriter, String> {
    let seed = random_u32();
    let connection_id = random_u32();
    let body = build_setup_body(seed, connection_id);
    let response = channel.request(
        "SETUP", "/", &body, "application/x-apple-binary-plist", &[],
        Duration::from_secs(8),
    ).await?;
    if response.status() != 200 {
        return Err(format!("type-130 SETUP rejected [{}]", response.first_line));
    }
    let port = parse_setup_response(&response.body, connection_id)?;
    let stream = factory.open_tcp(car_ip, port, 5_000)
        .ok_or_else(|| format!("type-130 TCP connect failed {car_ip}:{port}"))?;
    let (reader, writer) = stream.into_split();
    crate::emit_log(&format!("IAP-TUNNEL: type=130 dataPort={port} seed={seed} connectionId={connection_id}"));
    Ok(IapTunnelWriter { _reader: reader, writer, key: derive_key(shared_secret, seed), counter: 0 })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn type130_setup_contains_required_fields() {
        let body = build_setup_body(123, 456);
        let root = bplist::parse(&body).unwrap();
        let s = &root.get("streams").unwrap().as_array().unwrap()[0];
        assert_eq!(s.get("type").and_then(Value::as_int), Some(130));
        assert_eq!(s.get("seed").and_then(Value::as_int), Some(123));
        assert_eq!(s.get("streamConnectionID").and_then(Value::as_int), Some(456));
        assert_eq!(s.get("clientTypeUUID").and_then(Value::as_str), Some("E9459FD0-BCAD-4C45-820F-1E72447EF2F2"));
    }
    #[test]
    fn comm_package_header_and_payload_are_exact() {
        let p = build_comm_package(&[1,2,3]).unwrap();
        assert_eq!(u32::from_be_bytes(p[0..4].try_into().unwrap()), 35);
        assert_eq!(&p[16..20], &0x636f6d6du32.to_be_bytes());
        assert_eq!(&p[32..], &[1,2,3]);
    }
    #[test]
    fn encrypted_frame_authenticates_prefix_and_increments_by_frame() {
        let key=[7u8;32];
        let a=encrypt_frame(&key,0,b"one").unwrap();
        let b=encrypt_frame(&key,1,b"two").unwrap();
        assert_ne!(a,b);
        let n=u16::from_le_bytes(a[0..2].try_into().unwrap()) as usize;
        assert_eq!(n,a.len()-2);
        let plain=crate::crypto::chacha_open(&key,&nonce64(0),&a[2..],&a[0..2]).unwrap();
        assert_eq!(plain,b"one");
    }
    #[test]
    fn setup_response_requires_type130_and_nonzero_port() {
        let body = bplist::encode(&Value::dict(vec![("streams", Value::array(vec![
            Value::dict(vec![
                ("type", Value::int(130)),
                ("streamID", Value::int(1)),
                ("streamConnectionID", Value::int(77)),
                ("dataPort", Value::int(49152)),
            ]),
        ]))]));
        assert_eq!(parse_setup_response(&body, 77).unwrap(), 49152);
        assert!(parse_setup_response(&body, 78).is_err());
    }

    #[test]
    fn large_comm_package_is_split_into_independently_authenticated_frames() {
        let key = [9u8; 32];
        let payload = vec![0x5a; 65_520];
        let frames = encode_comm_frames(&key, 11, &build_comm_package(&payload).unwrap()).unwrap();
        assert_eq!(frames.len(), 2);
        let first = crate::crypto::chacha_open(&key, &nonce64(11), &frames[0][2..], &frames[0][..2]).unwrap();
        let second = crate::crypto::chacha_open(&key, &nonce64(12), &frames[1][2..], &frames[1][..2]).unwrap();
        assert_eq!(first.len(), u16::MAX as usize - 16);
        assert_eq!(first.len() + second.len(), 32 + payload.len());
    }
}
