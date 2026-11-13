import AVKit
import IPTVCore
import SwiftUI

struct LiveView: View {
  @EnvironmentObject private var model: AppModel
  @Environment(\.horizontalSizeClass) private var size
  @State private var query = ""
  @State private var category = ""
  @State private var selected: CatalogItem?
  @State private var favoritesOnly = false
  private var channels: [CatalogItem] {
    (model.catalogs[.live] ?? []).filter { item in
      (category.isEmpty || item.categoryID == category)
        && (!favoritesOnly || model.isFavorite(item))
        && query.split(whereSeparator: \.isWhitespace).allSatisfy {
          item.name.localizedCaseInsensitiveContains($0)
        }
    }
  }
  var body: some View {
    VStack(spacing: 12) {
      HStack {
        Picker(localized("Category"), selection: $category) {
          Text(localized("All categories")).tag("")
          ForEach(model.categories[.live] ?? []) { Text($0.name).tag($0.id) }
        }
        Toggle(localized("Favorites"), isOn: $favoritesOnly).toggleStyle(.button)
        Button {
          model.refresh()
        } label: {
          Label(localized("Refresh"), systemImage: "arrow.clockwise")
        }.labelStyle(.iconOnly)
      }.padding(.horizontal, 20)
      if let error = model.error {
        ErrorBanner(message: error, retry: { model.refresh() }).padding(.horizontal, 20)
      }
      if model.busy { ProgressView(localized("Loading channels")) }
      if let selected, size != .regular {
        NavigationLink(localized("Guide and preview") + ": " + selected.name) {
          ChannelGuideView(item: selected)
        }.buttonStyle(CinemaPrimaryButtonStyle()).padding(.horizontal, 20)
      }
      if size == .regular {
        HStack(spacing: 0) {
          channelList.frame(minWidth: 240, maxWidth: 360)
          Divider()
          if let selected {
            ChannelGuideView(item: selected).id(selected.key)
          } else {
            ContentUnavailableView(localized("Select channel"), systemImage: "tv")
          }
        }
      } else {
        channelList
      }

    }.searchable(text: $query, prompt: localized("Search channels")).background(Cinema.background)
      .onSubmit(of: .search) { model.rememberSearch(query) }
      .onChange(of: channels.map(\.key), initial: true) { _, keys in
        if selected.map({ !keys.contains($0.key) }) ?? true { selected = channels.first }
      }
  }
  @ViewBuilder private func channelTitle(_ item: CatalogItem) -> some View {
    if size == .regular {
      Button(item.name) { selected = item }.font(.headline).buttonStyle(.plain)
    } else {
      NavigationLink {
        ChannelGuideView(item: item)
      } label: {
        Text(item.name).font(.headline)
      }
    }
  }
  private var channelList: some View {
    List(channels, id: \.key) { item in
      HStack(spacing: 14) {
        Artwork(item: item).frame(width: 72, height: 48)
        channelTitle(item)
        Button {
          model.toggle(item)
        } label: {
          Image(systemName: model.isFavorite(item) ? "heart.fill" : "heart")
        }.buttonStyle(.borderless).frame(minWidth: 44, minHeight: 44).accessibilityLabel(
          localized("Toggle favorite"))
        Button {
          model.play(item)
        } label: {
          Image(systemName: "play.fill")
        }.buttonStyle(.borderless).frame(minWidth: 44, minHeight: 44).accessibilityLabel(
          localized("Watch live"))
      }.contextMenu { Button(localized("Select channel")) { selected = item } }
    }.listStyle(.plain).overlay {
      if channels.isEmpty && !model.busy {
        ContentUnavailableView(
          localized("No matching channels"), systemImage: "tv",
          description: Text(localized("Try another filter or refresh your provider.")))
      }
    }
  }

}
struct ChannelGuideView: View {
  @EnvironmentObject private var model: AppModel
  @Environment(\.scenePhase) private var phase
  let item: CatalogItem
  @State private var programmes: [Programme] = []
  @State private var error: String?
  @State private var loading = false
  @State private var preview: AVPlayer?
  @State private var previewError = false
  @State private var previewReady = false
  @State private var observation: NSKeyValueObservation?
  @State private var audioOwner = UUID()
  @State private var activationTask: Task<Void, Never>?
  var body: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: 20) {
        Artwork(item: item).frame(height: 150)
        Text(item.name).font(.title.bold())
        if let preview {
          VideoPlayer(player: preview).frame(minHeight: 220)
            .accessibilityIdentifier("live.preview")
            .accessibilityValue(localized(previewReady ? "Preview ready" : "Loading preview"))
            .overlay {
              if !previewReady && !previewError { ProgressView(localized("Loading preview")) }
            }
          if previewReady {
            Label(localized("Preview ready"), systemImage: "checkmark.circle.fill")
              .font(.caption).foregroundStyle(Cinema.accentText)
              .accessibilityIdentifier("live.preview.ready")
          }
          if previewError {
            ErrorBanner(message: localized("Preview unavailable. Try fullscreen playback."))
          }
        }
        ViewThatFits(in: .horizontal) {
          HStack { actions }
          VStack(alignment: .leading) { actions }
        }
        if loading { ProgressView(localized("Loading guide")) }
        if let error { ErrorBanner(message: error, retry: { Task { await load(force: true) } }) }
        TimelineView(.periodic(from: .now, by: 30)) { context in
          let now = context.date.timeIntervalSince1970
          VStack(alignment: .leading, spacing: 16) {
            CinemaHeading(text: "On this channel")
            if programmes.isEmpty && !loading {
              Text(localized("Program information unavailable")).foregroundStyle(CinemaSecondary())
            }
            ForEach(programmes) { programme in
              VStack(alignment: .leading, spacing: 6) {
                if programme.isCurrent(at: now) {
                  Label(localized("Now"), systemImage: "dot.radiowaves.left.and.right")
                    .foregroundStyle(
                      .red
                    ).font(.caption.bold())
                } else if programme.id == programmes.first(where: { $0.start > now })?.id {
                  Text(localized("Next")).font(.caption.bold()).foregroundStyle(Cinema.accentText)
                }
                Text(programme.title).font(.headline)
                Text(Date(timeIntervalSince1970: programme.start), style: .time) + Text(" – ")
                  + Text(Date(timeIntervalSince1970: programme.end), style: .time)
                if programme.isCurrent(at: now) {
                  ProgressView(
                    value: min(
                      1, max(0, (now - programme.start) / (programme.end - programme.start))))
                }
                if !programme.overview.isEmpty {
                  Text(programme.overview).foregroundStyle(CinemaSecondary())
                }
              }.padding(16).frame(maxWidth: .infinity, alignment: .leading).background(
                Cinema.panel, in: RoundedRectangle(cornerRadius: 12))
            }
            GuideTimeline(programmes: programmes, now: now)
          }
        }
        Button(localized("Refresh guide")) { Task { await load(force: true) } }.buttonStyle(
          .bordered)
      }.padding(24)
    }.background(Cinema.background).navigationTitle(item.name).navigationBarTitleDisplayMode(
      .inline
    )
    .task(id: item.key) {
      await load(force: false)
      while !Task.isCancelled {
        do {
          try await Task.sleep(for: .seconds(120))
          if phase == .active { await load(force: true) }
        } catch { return }
      }
    }
    .onDisappear { stopPreview() }
    .onChange(of: phase) { _, new in if new != .active { stopPreview() } }
    .onChange(of: model.playback?.id) { _, _ in stopPreview() }
  }
  @ViewBuilder private var actions: some View {
    Button(preview == nil ? localized("Start preview") : localized("Stop preview")) {
      if preview == nil { startPreview() } else { stopPreview() }
    }.buttonStyle(.bordered)
    Button(localized("Watch live"), systemImage: "play.fill") {
      stopPreview()
      model.play(item)
    }.buttonStyle(CinemaPrimaryButtonStyle())
    Button(
      model.isFavorite(item) ? localized("Saved") : localized("Favorite"), systemImage: "heart"
    ) {
      model.toggle(item)
    }.buttonStyle(.bordered)
  }
  private func load(force: Bool) async {
    loading = true
    error = nil
    do {
      let result = try await model.guide(item, force: force)
      try Task.checkCancellation()
      programmes = result
      loading = false
    } catch is CancellationError {} catch {
      self.error = friendly(error)
      loading = false
    }
  }
  private func startPreview() {
    guard let c = model.credentials,
      let url = try? model.provider.streamURL(c, item: item, transportStream: false)
    else {
      previewError = true
      return
    }
    let player = AVPlayer(url: url)
    preview = player
    previewError = false
    previewReady = false
    observation = player.currentItem?.observe(\.status, options: [.initial, .new]) { item, _ in
      Task { @MainActor in
        guard preview === player else { return }
        previewError = item.status == .failed
        previewReady = item.status == .readyToPlay
      }
    }
    activationTask = Task {
      do {
        try await AudioSessionController.shared.activate(owner: audioOwner, purpose: .playback)
        guard !Task.isCancelled, preview === player else { return }
        player.play()
      } catch is CancellationError {} catch { previewError = true }
    }
  }
  private func stopPreview() {
    activationTask?.cancel()
    activationTask = nil
    if preview != nil { AudioSessionController.shared.deactivate(owner: audioOwner) }
    observation = nil
    preview?.pause()
    preview?.replaceCurrentItem(with: nil)
    preview = nil
    previewReady = false
  }
}
struct GuideTimeline: View {
  let programmes: [Programme]
  let now: Double
  var body: some View {
    if let first = programmes.first, let last = programmes.last {
      let start = min(first.start, now)
      let end = max(last.end, now + 60)
      let span = end - start
      VStack(alignment: .leading, spacing: 8) {
        Text(localized("Schedule timeline")).font(.headline)
        ScrollView(.horizontal) {
          ZStack(alignment: .topLeading) {
            ForEach(programmes) { p in
              VStack(alignment: .leading) {
                Text(Date(timeIntervalSince1970: p.start), style: .time).font(.caption)
                Text(p.title).font(.caption.bold()).lineLimit(2)
              }
              .padding(8).frame(
                width: max(1, (p.end - p.start) / span * 1000), height: 80, alignment: .leading
              ).background(
                p.isCurrent(at: now) ? Color(hex: 0x7C3AED).opacity(0.4) : Cinema.panel
              ).offset(x: (p.start - start) / span * 1000)
            }
            Rectangle().fill(.red).frame(width: 2, height: 84).offset(
              x: max(0, (now - start) / span * 1000)
            ).accessibilityLabel(localized("Current time"))
          }.frame(width: 1000, height: 84)
        }
      }
    }
  }
}
