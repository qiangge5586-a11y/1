//! DiPlay 0.3.11 outbound iAP2 transport over AirPlay DataStream type 130.
//! The TCP connection has its own ChaCha20-Poly1305 counter, reset to zero per connection.

use std::sync::Arc;
use std::time::Duration;

use rand_core::{OsRng, RngCore};
use tokio::net::tcp::OwnedWriteHalf;

use crate::airplay::{self, SocketProvider};
use crate::bplist::{self, Value};
use crate::crypto::{chacha_open, chacha_seal, nonce64};
use crate::rtsp::RtspChannel;

const CLIENT_TYPE_UUID: &str = "E9459FD0-BCAD-4C45-820F-1E72447EF2F2";
const MAX_COMM_PACKAGE: usize = 4 * 1024 * 1024;
const TAG_BYTES: usize = 16;
const MAX_FRAME_PLAINTEXT: usize = u16::MAX as usize - TAG_BYTES;

pub struct IapTunnel {
    pub writer: OwnedWriteHalf,
    pub key: [u8; 32],
    pub seed: u64,
    pub connection_id: u64,
    pub data_port: u16,
}

fn random_nonnegative_u64() -> u64 {
    OsRng.next_u64() & i64::MAX as u64
}

pub fn build_setup_body(seed: u64, connection_id: u64) -> Vec<u8> {
    bplist::encode(&Value::dict(vec![(
        "streams",
        Value::array(vec![Value::dict(vec![
            ("type", Value::int(130)),
            ("streamConnectionID", Value::int(connection_id as i64)),
            ("clientTypeUUID", Value::str(CLIENT_TYPE_UUID)),
            ("seed", Value::int(seed as i64)),
        ])]),
    )]))
}

fn parse_setup_response(body: &[u8], connection_id: u64) -> Result<(u16, Option<i64>), String> {
    let root = bplist::parse(body).map_err(|e| format!("type-130 SETUP plist parse failed: {e}"))?;
    let streams = root.get("streams").and_then(Value::as_array)
        .ok_or_else(|| "type-130 SETUP response has no streams array".to_string())?;
    let stream = streams.iter().find(|s| {
        s.get("type").and_then(Value::as_int) == Some(130)
    }).or_else(|| streams.first())
        .ok_or_else(|| "type-130 SETUP response streams array is empty".to_string())?;
    if let Some(echo) = stream.get("streamConnectionID").and_then(Value::as_int) {
        if echo < 0 || echo as u64 != connection_id {
            return Err(format!("type-130 streamConnectionID mismatch: expected {connection_id}, got {echo}"));
        }
    }
    let data_port = stream.get("dataPort").and_then(Value::as_int)
        .filter(|p| (1..=u16::MAX as i64).contains(p))
        .ok_or_else(|| "type-130 SETUP response has no valid dataPort".to_string())? as u16;
    let stream_id = stream.get("streamID").and_then(Value::as_int);
    Ok((data_port, stream_id))
}

pub async fn setup(
    channel: &Arc<RtspChannel>,
    factory: &dyn SocketProvider,
    car_ip: &str,
    shared_secret: &[u8; 32],
) -> Result<IapTunnel, String> {
    let seed = random_nonnegative_u64();
    let connection_id = random_nonnegative_u64();
    let body = build_setup_body(seed, connection_id);
    let response = channel.request(
        "SETUP",
        "/",
        &body,
        "application/x-apple-binary-plist",
        &[],
        Duration::from_secs(8),
    ).await?;
    if response.status() != 200 {
        return Err(format!("type-130 SETUP rejected [{}]", response.first_line));
    }
    let (data_port, stream_id) = parse_setup_response(&response.body, connection_id)?;
    if let Some(id) = stream_id {
        if id != 1 {
            return Err(format!("type-130 SETUP returned unexpected streamID={id}, expected 1"));
        }
    }
    let tcp = factory.open_tcp(car_ip, data_port, 5_000)
        .ok_or_else(|| format!("type-130 TCP connect failed {car_ip}:{data_port}"))?;
    let (_reader, writer) = tcp.into_split();
    let key = airplay::stream_key(shared_secret, seed);
    Ok(IapTunnel { writer, key, seed, connection_id, data_port })
}

pub fn build_comm_package(payload: &[u8]) -> Result<Vec<u8>, String> {
    let total = 32usize.checked_add(payload.len())
        .ok_or_else(|| "iAP comm package length overflow".to_string())?;
    if total > MAX_COMM_PACKAGE || total > u32::MAX as usize {
        return Err(format!("iAP comm package exceeds DiPlay parser limit: {total} bytes"));
    }
    let mut packet = vec![0u8; total];
    packet[..4].copy_from_slice(&(total as u32).to_be_bytes());
    packet[16..20].copy_from_slice(&0x636f6d6du32.to_be_bytes());
    packet[32..].copy_from_slice(payload);
    Ok(packet)
}

/// Encodes one comm package into length-prefixed DataStream AEAD frames.
/// The 2-byte little-endian ciphertext+tag length is also the authenticated AAD.
pub fn encode_comm_frames(key: &[u8; 32], first_counter: u64, payload: &[u8]) -> Result<Vec<Vec<u8>>, String> {
    let package = build_comm_package(payload)?;
    let mut frames = Vec::new();
    let mut counter = first_counter;
    for chunk in package.chunks(MAX_FRAME_PLAINTEXT) {
        let cipher_len = chunk.len() + TAG_BYTES;
        let prefix = (cipher_len as u16).to_le_bytes();
        let sealed = chacha_seal(key, &nonce64(counter), chunk, &prefix);
        if sealed.len() != cipher_len {
            return Err("type-130 ChaCha20-Poly1305 encryption failed".to_string());
        }
        let mut frame = Vec::with_capacity(2 + sealed.len());
        frame.extend_from_slice(&prefix);
        frame.extend_from_slice(&sealed);
        frames.push(frame);
        counter = counter.wrapping_add(1);
    }
    Ok(frames)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn type130_setup_contains_exact_protocol_fields() {
        let body = build_setup_body(0x1234, 0x5678);
        let root = bplist::parse(&body).unwrap();
        let stream = &root.get("streams").unwrap().as_array().unwrap()[0];
        assert_eq!(stream.get("type").and_then(Value::as_int), Some(130));
        assert_eq!(stream.get("streamConnectionID").and_then(Value::as_int), Some(0x5678));
        assert_eq!(stream.get("seed").and_then(Value::as_int), Some(0x1234));
        assert_eq!(stream.get("clientTypeUUID").and_then(Value::as_str), Some(CLIENT_TYPE_UUID));
    }

    #[test]
    fn comm_header_and_encrypted_frames_round_trip() {
        let payload = [0xff, 0x55, 0x02, 0x00, 0xee, 0x10];
        let package = build_comm_package(&payload).unwrap();
        assert_eq!(u32::from_be_bytes(package[..4].try_into().unwrap()) as usize, package.len());
        assert_eq!(u32::from_be_bytes(package[16..20].try_into().unwrap()), 0x636f6d6d);
        assert_eq!(&package[32..], payload);
        let key = [0x5a; 32];
        let frames = encode_comm_frames(&key, 0, &payload).unwrap();
        assert_eq!(frames.len(), 1);
        let frame = &frames[0];
        let cipher_len = u16::from_le_bytes([frame[0], frame[1]]) as usize;
        assert_eq!(cipher_len, frame.len() - 2);
        let plain = chacha_open(&key, &nonce64(0), &frame[2..], &frame[..2]).unwrap();
        assert_eq!(plain, package);
    }

    #[test]
    fn oversized_comm_package_is_rejected() {
        let payload = vec![0u8; MAX_COMM_PACKAGE];
        assert!(build_comm_package(&payload).is_err());
    }
}
