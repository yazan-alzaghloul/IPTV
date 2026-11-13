import AVFoundation
import Foundation

/// Serializes potentially blocking audio-session calls away from the main actor.
/// An old preview or voice search cannot deactivate a newer fullscreen player.
final class AudioSessionController: @unchecked Sendable {
  enum Purpose: Sendable { case playback, voice }
  static let shared = AudioSessionController()
  private let queue = DispatchQueue(label: "IPTV.AudioSession", qos: .userInitiated)
  private var owner: UUID?

  func activate(owner: UUID, purpose: Purpose) async throws {
    try Task.checkCancellation()
    try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
      queue.async {
        do {
          let session = AVAudioSession.sharedInstance()
          if purpose == .voice {
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
          } else {
            try session.setCategory(.playback, mode: .moviePlayback)
          }
          try session.setActive(true)
          self.owner = owner
          continuation.resume()
        } catch {
          continuation.resume(throwing: error)
        }
      }
    }
  }

  func deactivate(owner: UUID) {
    queue.async {
      guard self.owner == owner else { return }
      self.owner = nil
      try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
  }
}
