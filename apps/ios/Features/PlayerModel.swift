import AVKit
import IPTVCore
import MediaPlayer
import SwiftUI

struct VideoQuality: Identifiable {
  let bitrate: Double
  let size: CGSize
  var id: Double { bitrate }
  var title: String { "\(Int(size.height))p · \(String(format: "%.1f", bitrate / 1_000_000)) Mbps" }
}
@MainActor final class PlayerModel: ObservableObject {
  let player = AVPlayer()
  let request: PlaybackRequest
  private let app: AppModel
  @Published private(set) var item: CatalogItem
  @Published private(set) var error: String?
  @Published private(set) var buffering = true
  @Published private(set) var qualities: [VideoQuality] = []
  @Published var locked = false
  @Published var aspect = "Fit"
  @Published var transportStream = false
  @Published var pictureInPicture = false
  @Published var quality: Double = 0 { didSet { applyQuality() } }
  private var retries = RetryBudget()
  private var playbackFailure: String?
  private var statusObservation: NSKeyValueObservation?
  private var bufferingObservation: NSKeyValueObservation?
  private var checkpoint: Any?
  private var endObserver: NSObjectProtocol?
  private var failureObserver: NSObjectProtocol?
  private var interruptionObserver: NSObjectProtocol?
  private var retryTask: Task<Void, Never>?
  private var assetTask: Task<Void, Never>?
  private var activationTask: Task<Void, Never>?
  private let audioOwner = UUID()
  private var previousChannel: CatalogItem?
  private var closed = false
  private var backgrounded = false
  private var generation = UUID()
  init(request: PlaybackRequest, app: AppModel) {
    self.request = request
    self.item = request.item
    self.app = app
    player.automaticallyWaitsToMinimizeStalling = true
    bufferingObservation = player.observe(\.timeControlStatus, options: [.new]) {
      [weak self] player, _ in
      Task { @MainActor in
        self?.buffering = player.timeControlStatus == .waitingToPlayAtSpecifiedRate
      }
    }
    checkpoint = player.addPeriodicTimeObserver(
      forInterval: CMTime(seconds: 5, preferredTimescale: 600), queue: .main
    ) { [weak self] _ in Task { @MainActor in self?.save() } }
    interruptionObserver = NotificationCenter.default.addObserver(
      forName: AVAudioSession.interruptionNotification, object: nil, queue: .main
    ) { [weak self] note in
      let type = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
      let options = note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
      Task { @MainActor in
        if type == AVAudioSession.InterruptionType.began.rawValue {
          self?.player.pause()
          self?.save()
        } else if options & AVAudioSession.InterruptionOptions.shouldResume.rawValue != 0 {
          self?.player.play()
        }
      }
    }
    open(restart: request.restart)
  }
  var index: Int? { request.queue.firstIndex { $0.key == item.key } }
  var hasPrevious: Bool { (index ?? 0) > 0 }
  var hasNext: Bool {
    if let index { return index + 1 < request.queue.count }
    return false
  }
  var canReturn: Bool { previousChannel != nil }
  var gravity: AVLayerVideoGravity {
    aspect == "Fill" ? .resizeAspectFill : aspect == "Stretch" ? .resize : .resizeAspect
  }
  func retry() {
    save()
    retries.reset()
    open()
  }
  func toggleTransport() {
    save()
    transportStream.toggle()
    retry()
  }
  func switchBy(_ delta: Int) {
    guard let index, request.queue.indices.contains(index + delta) else { return }
    select(request.queue[index + delta])
  }
  func returnChannel() { if let previousChannel { select(previousChannel) } }
  func select(_ next: CatalogItem) {
    guard next.key != item.key else { return }
    save()
    if item.kind == .live { previousChannel = item }
    item = next
    retries.reset()
    transportStream = false
    quality = 0
    open()
  }
  private func open(restart: Bool = false) {
    guard !closed else { return }
    retryTask?.cancel()
    assetTask?.cancel()
    activationTask?.cancel()
    statusObservation = nil
    if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
    if let failureObserver { NotificationCenter.default.removeObserver(failureObserver) }
    generation = UUID()
    let token = generation
    error = nil
    playbackFailure = nil
    buffering = true
    qualities = []
    locked = false
    let resume = restart ? 0 : app.library.position(item)
    do {
      let url = try app.provider.streamURL(
        request.credentials, item: item, transportStream: transportStream)
      let asset = AVURLAsset(url: url)
      let playerItem = AVPlayerItem(asset: asset)
      let title = AVMutableMetadataItem()
      title.identifier = .commonIdentifierTitle
      title.value = item.name as NSString
      title.extendedLanguageTag = "und"
      playerItem.externalMetadata = [title]
      player.replaceCurrentItem(with: playerItem)
      statusObservation = playerItem.observe(\.status, options: [.new, .initial]) {
        [weak self] observed, _ in
        let status = observed.status
        let failure = observed.error
        Task { @MainActor in
          guard let self, self.generation == token, !self.closed else { return }
          if status == .readyToPlay {
            self.buffering = false
            if resume > 0 && self.item.kind != .live {
              let duration = observed.duration.seconds
              let target =
                duration.isFinite && duration > 0 ? min(resume, max(0, duration - 1)) : resume
              self.player.seek(
                to: CMTime(seconds: target, preferredTimescale: 600), toleranceBefore: .zero,
                toleranceAfter: .zero)
            }
          } else if status == .failed {
            self.failed(failure)
          }
        }
      }
      endObserver = NotificationCenter.default.addObserver(
        forName: .AVPlayerItemDidPlayToEndTime, object: playerItem, queue: .main
      ) { [weak self] _ in
        Task { @MainActor in
          guard let self, self.generation == token else { return }
          self.save(ended: true)
          if self.item.parentSeriesID != nil && self.app.autoplay && self.hasNext {
            self.switchBy(1)
          }
        }
      }
      failureObserver = NotificationCenter.default.addObserver(
        forName: .AVPlayerItemFailedToPlayToEndTime, object: playerItem, queue: .main
      ) { [weak self] notification in
        let failure = notification.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error
        Task { @MainActor in
          guard self?.generation == token else { return }
          self?.failed(failure)
        }
      }
      assetTask = Task { [weak self] in
        let variants = (try? await asset.load(.variants)) ?? []
        guard let self, self.generation == token, !Task.isCancelled else { return }
        var seen = Set<Double>()
        self.qualities = variants.compactMap { variant in
          let rate = variant.peakBitRate ?? variant.averageBitRate ?? 0
          let size = variant.videoAttributes?.presentationSize ?? .zero
          guard rate > 0, size.height > 0, seen.insert(rate).inserted else { return nil }
          return VideoQuality(bitrate: rate, size: size)
        }.sorted { $0.bitrate < $1.bitrate }
        self.applyQuality()
      }
      activationTask = Task { [weak self] in
        guard let self else { return }
        do {
          try await AudioSessionController.shared.activate(owner: audioOwner, purpose: .playback)
          guard !Task.isCancelled, generation == token, !closed, !backgrounded || pictureInPicture
          else { return }
          player.play()
        } catch is CancellationError {} catch {
          if generation == token, !closed {
            self.error = friendly(error)
            buffering = false
          }
        }
      }
    } catch {
      self.error = friendly(error)
      buffering = false
    }
  }
  private func failed(_ error: Error?) {
    guard retryTask == nil else { return }
    var failure = error as NSError?
    var codes: [String] = []
    for _ in 0..<6 {
      guard let current = failure else { break }
      codes.append("\(current.domain) (\(current.code))")
      failure = current.userInfo[NSUnderlyingErrorKey] as? NSError
    }
    playbackFailure = codes.isEmpty ? "No underlying error was provided." : codes.joined(separator: " → ")
    locked = false
    buffering = false
    var underlying = error as NSError?
    var network = false
    for _ in 0..<6 {
      guard let e = underlying else { break }
      if e.domain == NSURLErrorDomain,
        [
          NSURLErrorTimedOut, NSURLErrorNetworkConnectionLost, NSURLErrorNotConnectedToInternet,
          NSURLErrorCannotConnectToHost, NSURLErrorCannotFindHost,
        ].contains(e.code)
      {
        network = true
      }
      underlying = e.userInfo[NSUnderlyingErrorKey] as? NSError
    }
    if network, let delay = retries.nextDelay() {
      self.error = localized("Connection interrupted. Reconnecting…")
      retryTask = Task { [weak self] in
        do {
          try await Task.sleep(for: .seconds(delay))
          guard let self, !self.closed else { return }
          self.retryTask = nil
          self.open()
        } catch {}
      }
    } else {
      self.error =
        network
        ? localized("Connection interrupted. Check your network, then retry.")
        : localized(
          "This stream is unavailable or unsupported on this device. Try HLS or another title.")
    }
  }
  private func applyQuality() {
    player.currentItem?.preferredPeakBitRate = quality
    player.currentItem?.preferredMaximumResolution =
      qualities.first(where: { $0.bitrate == quality })?.size ?? .zero
  }
  func save(ended: Bool = false) {
    guard !closed, player.currentItem?.status == .readyToPlay else { return }
    let duration = player.currentItem?.duration.seconds ?? 0
    let position =
      ended && duration.isFinite && duration > 0 ? duration : player.currentTime().seconds
    app.savePosition(item, position: position, duration: duration)
  }
  var diagnostics: String {
    let size = player.currentItem?.presentationSize ?? .zero
    let bitrate = player.currentItem?.accessLog()?.events.last?.observedBitrate ?? 0
    return [
      "AVPlayer", "Status: \(player.currentItem.map { String(describing: $0.status) } ?? "No item")",
      "\(Int(size.width)) × \(Int(size.height))",
      "\(String(format: "%.1f", bitrate / 1_000_000)) Mbps",
      item.kind == .live ? (transportStream ? "MPEG-TS" : "HLS") : item.container.uppercased(),
      playbackFailure.map { "Failure: \($0)" } ?? "No playback failure recorded.",
    ].joined(separator: "\n")
  }
  func foreground() { backgrounded = false }
  func background() {
    backgrounded = true
    save()
    if !pictureInPicture { player.pause() }
  }
  func close() {
    guard !closed else { return }
    save()
    closed = true
    retryTask?.cancel()
    assetTask?.cancel()
    activationTask?.cancel()
    player.pause()
    player.replaceCurrentItem(with: nil)
    statusObservation = nil
    bufferingObservation = nil
    if let checkpoint {
      player.removeTimeObserver(checkpoint)
      self.checkpoint = nil
    }
    for observer in [endObserver, failureObserver, interruptionObserver].compactMap({ $0 }) {
      NotificationCenter.default.removeObserver(observer)
    }
    endObserver = nil
    failureObserver = nil
    interruptionObserver = nil
    AudioSessionController.shared.deactivate(owner: audioOwner)
  }
}
