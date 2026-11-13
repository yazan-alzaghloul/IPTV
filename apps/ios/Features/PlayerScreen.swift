import AVKit
import IPTVCore
import SwiftUI

struct PlayerScreen: View {
  @StateObject private var model: PlayerModel
  init(request: PlaybackRequest, app: AppModel) {
    _model = StateObject(wrappedValue: PlayerModel(request: request, app: app))
  }
  var body: some View { PlayerContainer(model: model) }
}
private struct PlayerContainer: View {
  @StateObject var model: PlayerModel
  @EnvironmentObject private var app: AppModel
  @Environment(\.dismiss) private var dismiss
  @Environment(\.scenePhase) private var phase
  @State private var info = false
  @State private var episodes = false
  var body: some View {
    VStack(spacing: 0) {
      if !model.locked {
        VStack(alignment: .leading, spacing: 10) {
          HStack {
            Button(localized("Close"), systemImage: "xmark") {
              model.close()
              dismiss()
            }.buttonStyle(.bordered)
            Text(model.item.name).font(.headline).lineLimit(2)
            Spacer()
          }
          ScrollView(.horizontal) {
            HStack {
              Menu(localized("Aspect")) {
                ForEach(["Fit", "Fill", "Stretch"], id: \.self) { value in
                  Button(localized(value)) { model.aspect = value }
                }
              }
              if model.qualities.count > 1 {
                Menu(localized("Quality")) {
                  Button(localized("Automatic")) { model.quality = 0 }
                  ForEach(model.qualities) { quality in
                    Button(localized("Up to") + " " + quality.title) {
                      model.quality = quality.bitrate
                    }
                  }
                }
              }
              Button(localized("Info")) { info = true }
              Button(localized("Lock controls"), systemImage: "lock") { model.locked = true }
              if model.item.kind == .live {
                Button(localized("Channel −")) { model.switchBy(-1) }.disabled(!model.hasPrevious)
                Button(localized("Channel +")) { model.switchBy(1) }.disabled(!model.hasNext)
                Button(localized("Previous channel")) { model.returnChannel() }.disabled(
                  !model.canReturn)
              } else if model.item.parentSeriesID != nil {
                Button(localized("Previous episode")) { model.switchBy(-1) }.disabled(
                  !model.hasPrevious)
                Button(localized("Next episode")) { model.switchBy(1) }.disabled(!model.hasNext)
                Button(localized("Episodes")) { episodes = true }
                Toggle(localized("Autoplay"), isOn: $app.autoplay).toggleStyle(.button)
              }
            }.buttonStyle(.bordered)
          }.scrollIndicators(.hidden)
        }.padding(12)
      }
      ZStack(alignment: .topTrailing) {
        NativePlayer(model: model).allowsHitTesting(!model.locked)
        if model.locked {
          Button(localized("Unlock controls"), systemImage: "lock.open") { model.locked = false }
            .buttonStyle(CinemaPrimaryButtonStyle()).padding(16)
        }
        if model.buffering {
          ProgressView(localized("Buffering")).padding().frame(
            maxWidth: .infinity, maxHeight: .infinity
          )
          .allowsHitTesting(false)
        }
      }
      if let error = model.error {
        VStack(alignment: .leading) {
          Text(error)
          HStack {
            Button(localized("Retry")) { model.retry() }
            if model.item.kind == .live {
              Button(model.transportStream ? localized("Try HLS") : localized("Try MPEG-TS")) {
                model.toggleTransport()
              }
            }
          }.buttonStyle(CinemaPrimaryButtonStyle())
        }.padding().frame(maxWidth: .infinity, alignment: .leading)
      }
    }.background(.black).foregroundStyle(.white).preferredColorScheme(.dark)
      .interactiveDismissDisabled(model.locked || model.pictureInPicture)
      .alert(localized("Playback diagnostics"), isPresented: $info) {
        Button(localized("Close"), role: .cancel) {}
      } message: {
        Text(model.diagnostics)
      }
      .sheet(isPresented: $episodes) {
        NavigationStack {
          List(model.request.queue, id: \.key) { item in
            Button("S\(item.season ?? 0) · E\(item.episode ?? 0)  \(item.name)") {
              model.select(item)
              episodes = false
            }
          }.navigationTitle(localized("Episodes")).toolbar {
            Button(localized("Close")) { episodes = false }
          }
        }
      }
      .onChange(of: phase) { _, new in
        if new != .active { model.background() } else { model.foreground() }
      }
      .onDisappear { model.close() }
  }
}
private struct NativePlayer: UIViewControllerRepresentable {
  @ObservedObject var model: PlayerModel
  func makeCoordinator() -> Coordinator { Coordinator(model: model) }
  func makeUIViewController(context: Context) -> AVPlayerViewController {
    let controller = AVPlayerViewController()
    controller.player = model.player
    controller.delegate = context.coordinator
    controller.allowsPictureInPicturePlayback = true
    controller.canStartPictureInPictureAutomaticallyFromInline = true
    controller.updatesNowPlayingInfoCenter = true
    return controller
  }
  func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {
    controller.videoGravity = model.gravity
    controller.showsPlaybackControls = !model.locked
  }
  final class Coordinator: NSObject, AVPlayerViewControllerDelegate {
    private let model: PlayerModel
    init(model: PlayerModel) { self.model = model }
    func playerViewControllerWillStartPictureInPicture(
      _ playerViewController: AVPlayerViewController
    ) { Task { @MainActor in model.pictureInPicture = true } }
    func playerViewControllerDidStopPictureInPicture(_ playerViewController: AVPlayerViewController)
    { Task { @MainActor in model.pictureInPicture = false } }
    func playerViewController(
      _ playerViewController: AVPlayerViewController,
      failedToStartPictureInPictureWithError error: Error
    ) { Task { @MainActor in model.pictureInPicture = false } }
    func playerViewController(
      _ playerViewController: AVPlayerViewController,
      restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler:
        @escaping (Bool) -> Void
    ) { completionHandler(true) }
  }
}
