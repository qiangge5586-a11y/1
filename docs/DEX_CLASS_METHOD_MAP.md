# DEX Class and Method Map (Structural)

Date: 2026-11-04

## Scope
Parsed the verified original APK's `classes.dex` header, string/type/method tables, class definitions, and encoded class_data. This recovers class descriptors, method names, access flags and whether a method has a code item. It does **not** decompile instruction bodies or establish runtime behavior.

Sample SHA-256: `e52aed97a77bc4be1e4d248bb9526edafaae428ea20b8926f7763e532cc55fbe`

## Header and namespace counts
- DEX magic: `dex\\n035`
- String IDs: 12,302
- Type IDs: 2,555
- Method IDs: 15,752
- Class definitions: 1,770
- `cn.autohack` namespace classes: 427
- `cn.autohack.hondahack` namespace classes: 353

## Readable application classes and method names

| Class | Method names observed |
|---|---|
| AboutActivity | `onCreate`, `onOptionsItemSelected` |
| AdvancedActivity | `onCreate`, `onOptionsItemSelected`, `onPreferenceTreeClick` |
| AppsActivity | `onActivityResult`, `onCreate`, `onOptionsItemSelected`, `onPreferenceTreeClick` |
| AutorunManagerActivity | constructor, `asBinder`, obfuscated `a` |
| CleanInternalSdActivity | `onCreate` |
| EmergencyActivity | parcelable methods `createFromParcel`, `newArray`, obfuscated `f/g` |
| FixActivity | obfuscated `i/q` |
| LogActivity | constructor, obfuscated `C` |
| MainActivity | obfuscated `a` |
| MeterActivity | `onCreate`, `onStart`, `onStop`, `onSharedPreferenceChanged`, `onOptionsItemSelected` |
| MyBroadcastReceiver | `onReceive` |
| MyService | `onBind`, `asBinder`, multiple obfuscated service methods |
| NavigatorActivity | `onTransact`, `asBinder`, obfuscated `a/b` |
| OptimizeActivity | `onCreate`, `onOptionsItemSelected`, `onPreferenceTreeClick` |
| RemapKeysActivity | constructor and obfuscated methods |
| SdcardActivity | `onCreate`, `onOptionsItemSelected`, `onPreferenceTreeClick` |
| SettingsActivity | `onCreate`, `onStart`, `onStop`, `onSharedPreferenceChanged`, `onPreferenceTreeClick` |
| StartupActivity | `onTransact`, `asBinder`, obfuscated methods |
| StartupVideoActivity | `onCreate`, `onOptionsItemSelected`, `onPreferenceTreeClick` |
| TestActivity | `onCreate`, `onStart`, `onStop` |
| VoiceAssistantActivity | `onCreate`, `onOptionsItemSelected`, `onPreferenceTreeClick` |
| Xposed | constructor, `getVersion` |
| VoiceAssistantService | `onCancel`, `asBinder`, obfuscated methods |

Inner classes may appear separately in the DEX and are not all listed. Manifest-declared app-install and video-selection activities have mostly obfuscated methods and need instruction-level review.

## Implementation mapping implications
- Several screens are preference-based Android activities; lifecycle and preference callbacks are useful entry points.
- Priority targets for deeper analysis: `MeterActivity`, `NavigatorActivity`, `StartupActivity`, `RemapKeysActivity`, `SdcardActivity`, and `VoiceAssistantActivity`.
- `MyService` and `MyBroadcastReceiver` are central service/event entry points and need careful review before reproducing background behavior.
- `Xposed` and the signature-hook application class remain excluded from the safe rebuild.

## Limitations and next step
Method identifiers and class_data are structural evidence only. Obfuscated methods such as `a`, `b`, and `onTransact` cannot be assigned feature semantics from names alone. Next, use JADX to decompile authorized APK/DEX content and inspect methods against the feature traceability matrix. The official project documents APK/DEX decompilation and manifest/resource decoding, while warning that some code may not decompile perfectly: https://github.com/skylot/jadx
