@preconcurrency import AVFoundation
import Foundation
import UniformTypeIdentifiers

// Convert the existing licensed MP4 sample into video-only H.264/fMP4 test HLS.
// This utility is never compiled into either application.
final class SegmentSink: NSObject, AVAssetWriterDelegate {
  let directory: URL
  private(set) var entries: [(String, Double)] = []
  private(set) var failure: Error?
  init(directory: URL) { self.directory = directory }
  func assetWriter(
    _ writer: AVAssetWriter, didOutputSegmentData data: Data,
    segmentType: AVAssetSegmentType, segmentReport: AVAssetSegmentReport?
  ) {
    do {
      if segmentType == .initialization {
        try data.write(to: directory.appendingPathComponent("init.mp4"))
      } else {
        let name = "segment-\(entries.count).m4s"
        try data.write(to: directory.appendingPathComponent(name))
        let seconds = segmentReport?.trackReports.first?.duration.seconds ?? 1
        entries.append((name, seconds.isFinite && seconds > 0 ? seconds : 1))
      }
    } catch { failure = error }
  }
  func finish() throws {
    if let failure { throw failure }
    guard !entries.isEmpty else { throw NSError(domain: "Fixture", code: 1) }
    let target = Int(ceil(entries.map(\.1).max() ?? 1))
    var playlist =
      "#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:\(target)\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXT-X-MAP:URI=\"init.mp4\"\n"
    for (file, duration) in entries { playlist += "#EXTINF:\(duration),\n\(file)\n" }
    playlist += "#EXT-X-ENDLIST\n"
    try playlist.write(
      to: directory.appendingPathComponent("index.m3u8"), atomically: true, encoding: .utf8)
  }
}
@main struct HLSFixtureGenerator {
  static func main() async throws {
    guard CommandLine.arguments.count == 3 else {
      throw NSError(
        domain: "Fixture", code: 4,
        userInfo: [NSLocalizedDescriptionKey: "Usage: generator input.mp4 output-directory"])
    }
    let source = URL(fileURLWithPath: CommandLine.arguments[1])
    let directory = URL(fileURLWithPath: CommandLine.arguments[2], isDirectory: true)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    let asset = AVURLAsset(url: source)
    guard let track = try await asset.loadTracks(withMediaType: .video).first else {
      throw NSError(
        domain: "Fixture", code: 5, userInfo: [NSLocalizedDescriptionKey: "Sample has no video"])
    }
    let size = try await track.load(.naturalSize)
    let reader = try AVAssetReader(asset: asset)
    let output = AVAssetReaderTrackOutput(
      track: track,
      outputSettings: [
        kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange
      ])
    reader.add(output)
    let writer = AVAssetWriter(contentType: .mpeg4Movie)
    let sink = SegmentSink(directory: directory)
    writer.delegate = sink
    writer.outputFileTypeProfile = .mpeg4AppleHLS
    writer.preferredOutputSegmentInterval = CMTime(seconds: 1, preferredTimescale: 600)
    writer.initialSegmentStartTime = .zero
    let input = AVAssetWriterInput(
      mediaType: .video,
      outputSettings: [
        AVVideoCodecKey: AVVideoCodecType.h264,
        AVVideoWidthKey: Int(size.width), AVVideoHeightKey: Int(size.height),
        AVVideoCompressionPropertiesKey: [
          AVVideoAverageBitRateKey: 400_000, AVVideoMaxKeyFrameIntervalDurationKey: 1,
          AVVideoAllowFrameReorderingKey: false,
        ],
      ])
    writer.add(input)
    guard writer.startWriting(), reader.startReading() else {
      throw writer.error ?? reader.error ?? NSError(domain: "Fixture", code: 2)
    }
    writer.startSession(atSourceTime: .zero)
    await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
      input.requestMediaDataWhenReady(on: DispatchQueue(label: "Fixture.Encode")) {
        while input.isReadyForMoreMediaData {
          if let sample = output.copyNextSampleBuffer() {
            if !input.append(sample) {
              input.markAsFinished()
              reader.cancelReading()
              writer.finishWriting { continuation.resume() }
              return
            }
          } else {
            input.markAsFinished()
            writer.finishWriting { continuation.resume() }
            return
          }
        }
      }
    }
    guard writer.status == .completed else {
      throw writer.error ?? NSError(domain: "Fixture", code: 3)
    }
    try sink.finish()
    print("Generated \(sink.entries.count) H.264 HLS segments")
  }
}
