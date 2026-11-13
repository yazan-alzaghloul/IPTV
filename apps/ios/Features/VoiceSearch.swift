import AVFoundation
import Speech
import SwiftUI

@MainActor final class VoiceSearch: ObservableObject {
  @Published private(set) var transcript = ""
  @Published private(set) var isListening = false
  @Published var error: String?
  private let engine = AVAudioEngine()
  private var task: SFSpeechRecognitionTask?
  private var request: SFSpeechAudioBufferRecognitionRequest?
  private var tapInstalled = false
  private var audioOwner: UUID?
  private var permissionTask: Task<Void, Never>?
  private var generation = UUID()
  func start() {
    guard !isListening, permissionTask == nil else { return }
    error = nil
    generation = UUID()
    let token = generation
    permissionTask = Task { [weak self] in
      guard let self else { return }
      defer { if generation == token { permissionTask = nil } }
      let speech = await withCheckedContinuation { continuation in
        SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0) }
      }
      guard !Task.isCancelled, generation == token else { return }
      let microphone = await AVAudioApplication.requestRecordPermission()
      guard !Task.isCancelled, generation == token else { return }
      guard speech == .authorized, microphone else {
        error = localized(
          "Allow microphone and speech recognition in Settings to use voice search.")
        return
      }
      guard let recognizer = SFSpeechRecognizer(locale: .current), recognizer.isAvailable else {
        error = localized("Voice search is unavailable. Type your search instead.")
        return
      }
      do {
        audioOwner = token
        try await AudioSessionController.shared.activate(owner: token, purpose: .voice)
        guard !Task.isCancelled, generation == token else {
          AudioSessionController.shared.deactivate(owner: token)
          return
        }
        let request = SFSpeechAudioBufferRecognitionRequest()
        self.request = request
        request.shouldReportPartialResults = true
        let input = engine.inputNode
        let format = engine.inputNode.outputFormat(forBus: 0)
        guard format.sampleRate > 0, format.channelCount > 0 else {
          throw URLError(.cannotOpenFile)
        }
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
          request.append(buffer)
        }
        tapInstalled = true
        self.task = recognizer.recognitionTask(with: request) { [weak self] result, failure in
          Task { @MainActor in
            if let result { self?.transcript = result.bestTranscription.formattedString }
            if failure != nil || result?.isFinal == true { self?.stop() }
          }
        }
        engine.prepare()
        try engine.start()
        isListening = true
      } catch {
        stop()
        self.error = localized("Voice search is unavailable. Type your search instead.")
      }
    }
  }
  func stop() {
    generation = UUID()
    permissionTask?.cancel()
    permissionTask = nil
    engine.stop()
    if tapInstalled {
      engine.inputNode.removeTap(onBus: 0)
      tapInstalled = false
    }
    request?.endAudio()
    task?.cancel()
    task = nil
    request = nil
    isListening = false
    if let audioOwner {
      AudioSessionController.shared.deactivate(owner: audioOwner)
      self.audioOwner = nil
    }
  }
}
