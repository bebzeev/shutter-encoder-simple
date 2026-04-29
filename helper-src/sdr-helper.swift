// sdr-helper — bundled native macOS HDR→SDR converter.
//
// Bundled inside Contents/MacOS/sdr-helper of the .app. Java spawns this as a
// subprocess instead of /usr/bin/avconvert. Native AVFoundation code running
// inside our own .app gets the right Cocoa context to open files via NSURL +
// AVAssetExportSession — which avconvert-as-subprocess does not when the
// parent is a Java/JVM process (the JVM's Swing drag-drop doesn't issue the
// security-scoped URL tokens that Cocoa drag-drop does).
//
// Usage: sdr-helper <source> <destination>
// Output:
//   "progress: NN% complete" lines on stdout while exporting (Java parses).
//   Non-zero exit code on failure with the NSError description on stderr.

import Foundation
import AVFoundation

let stderr = FileHandle.standardError

func writeError(_ msg: String) {
    if let data = (msg + "\n").data(using: .utf8) {
        stderr.write(data)
    }
}

guard CommandLine.arguments.count >= 3 else {
    writeError("Usage: sdr-helper <source> <destination>")
    exit(2)
}

let sourcePath = CommandLine.arguments[1]
let destPath   = CommandLine.arguments[2]
let sourceURL  = URL(fileURLWithPath: sourcePath)
let destURL    = URL(fileURLWithPath: destPath)

// Pre-clean destination — AVAssetExportSession refuses to overwrite.
if FileManager.default.fileExists(atPath: destPath) {
    do {
        try FileManager.default.removeItem(at: destURL)
    } catch {
        writeError("Could not remove existing destination: \(error.localizedDescription)")
        exit(3)
    }
}

let asset = AVAsset(url: sourceURL)

// Diagnostic: list compatible presets (helps debug if HighestQuality fails for
// a particular asset).
let compatible = AVAssetExportSession.exportPresets(compatibleWith: asset)
print("compatible presets: \(compatible.joined(separator: ", "))")

let presetName = AVAssetExportPresetHighestQuality

guard let exporter = AVAssetExportSession(asset: asset, presetName: presetName) else {
    writeError("Could not create AVAssetExportSession for preset \(presetName)")
    exit(4)
}

exporter.outputURL      = destURL
exporter.outputFileType = .mov
exporter.shouldOptimizeForNetworkUse = false

// Kick off async export and poll progress until it signals completion.
let group = DispatchGroup()
group.enter()
exporter.exportAsynchronously {
    group.leave()
}

while group.wait(timeout: .now() + 0.5) == .timedOut {
    let pct = Int(exporter.progress * 100)
    print("progress: \(pct)% complete")
    fflush(stdout)
}

switch exporter.status {
case .completed:
    print("progress: 100% complete")
    print("Export completed.")
    exit(0)
case .failed:
    let nserror = exporter.error as NSError?
    let desc = nserror?.localizedDescription ?? "unknown error"
    let domain = nserror?.domain ?? "?"
    let code = nserror?.code ?? -1
    writeError("Export failed: \(desc) [\(domain) \(code)]")
    if let info = nserror?.userInfo, !info.isEmpty {
        writeError("userInfo: \(info)")
    }
    exit(1)
case .cancelled:
    writeError("Export cancelled")
    exit(5)
default:
    writeError("Unexpected status: \(exporter.status.rawValue)")
    exit(6)
}
