import IPTVCore
import SwiftUI

struct DetailsView: View {
  @EnvironmentObject private var model: AppModel
  let item: CatalogItem
  @State private var details: ContentDetails?
  @State private var loading = true
  @State private var error: String?
  @State private var selectedSeason: Int?
  @State private var technical = false
  @State private var related: [CatalogItem] = []
  private var episodes: [Episode] { details?.episodes ?? [] }
  private var continuation: Episode? {
    MediaRules.continuation(
      seriesID: item.id, episodes: episodes, history: model.library.history,
      watched: model.library.watched)
  }
  private var seasons: [Int] { Array(Set(episodes.map(\.season))).sorted() }
  private var activeSeason: Int? {
    selectedSeason ?? model.library.seasons[item.id] ?? seasons.first
  }
  var body: some View {
    ScrollView {
      LazyVStack(alignment: .leading, spacing: 20) {
        FeaturedHero(item: item, details: details, showDetails: false)
        VStack(alignment: .leading, spacing: 16) {
          if loading { ProgressView(localized("Loading details")) }
          if let error { ErrorBanner(message: error, retry: { Task { await load() } }) }
          if item.kind == .live {
            NavigationLink(localized("Channel guide")) { ChannelGuideView(item: item) }.buttonStyle(
              .bordered)
          } else {
            Text(
              details?.overview.isEmpty == false
                ? details!.overview : localized("Description unavailable")
            ).modifier(ScaledText())
            if let rating = details?.rating, (Double(rating) ?? 0) > 0 {
              fact("Provider rating", rating)
            }
            if let details {
              fact("Cast", details.cast)
              fact("Director", details.director)
              fact("Provider languages", details.language)
              fact("Provider subtitles", details.subtitles)
              fact("Content rating", details.classification)
              if let trailer = details.trailer {
                Link(destination: trailer) {
                  Label(localized("Watch trailer"), systemImage: "play.rectangle")
                }.buttonStyle(.bordered)
              }
            }
            if item.kind == .movie {
              Button(localized("Restart from beginning")) {
                model.play(item, details: details, restart: true)
              }.buttonStyle(.bordered)
              Button(
                model.library.watched.contains(item.key)
                  ? localized("Mark unwatched") : localized("Mark watched")
              ) { model.mark([item], watched: !model.library.watched.contains(item.key)) }
              .buttonStyle(.bordered)
            }
            if item.kind == .series {
              seasonPicker
              episodeList
            }
            DisclosureGroup(localized("Streaming information"), isExpanded: $technical) {
              VStack(alignment: .leading) {
                fact("Provider video", details?.technical ?? "")
                fact("Provider container", details?.container ?? item.container)
              }.frame(maxWidth: .infinity, alignment: .leading)
            }
          }
        }.padding(.horizontal, 24)
        MediaRail(title: item.kind == .movie ? "Related movies" : "Related series", items: related)
      }.padding(.vertical, 16)
    }.background(Cinema.background).navigationTitle(item.name).navigationBarTitleDisplayMode(
      .inline
    )
    .task(id: item.key) { await load() }
  }
  @ViewBuilder private func fact(_ title: String, _ value: String) -> some View {
    if !value.isEmpty {
      VStack(alignment: .leading, spacing: 4) {
        Text(localized(title)).font(.headline)
        Text(value).foregroundStyle(CinemaSecondary())
      }
    }
  }
  @ViewBuilder private var seasonPicker: some View {
    if !seasons.isEmpty {
      Picker(
        localized("Season"),
        selection: Binding(
          get: { seasons.contains(activeSeason ?? -1) ? activeSeason ?? seasons[0] : seasons[0] },
          set: {
            selectedSeason = $0
            model.selectSeason($0, seriesID: item.id)
          })
      ) {
        ForEach(seasons, id: \.self) { Text(localized("Season") + " \($0)").tag($0) }
      }.pickerStyle(.menu)
      let selected = episodes.filter { $0.season == activeSeason }.map(\.item)
      let watched =
        !selected.isEmpty && selected.allSatisfy { model.library.watched.contains($0.key) }
      Button(watched ? localized("Mark season unwatched") : localized("Mark season watched")) {
        let season = activeSeason
        selectedSeason = season
        let current = episodes.filter { $0.season == season }.map(\.item)
        model.mark(current, watched: !watched)
      }.buttonStyle(.bordered).frame(minHeight: 44)
        .accessibilityIdentifier("series.markSeason")
        .accessibilityValue("\(selected.count)")
      Toggle(localized("Autoplay next episode"), isOn: $model.autoplay)
    } else if !loading {
      Text(localized("No episodes available from this provider.")).foregroundStyle(
        CinemaSecondary())
    }
  }
  @ViewBuilder private var episodeList: some View {
    ForEach(episodes.filter { $0.season == activeSeason }) { episode in
      VStack(alignment: .leading, spacing: 12) {
        Artwork(item: episode.item).aspectRatio(16 / 9, contentMode: .fit).frame(maxHeight: 200)
          .clipped()
        Text("S\(episode.season) · E\(episode.number)  \(episode.item.name)").font(.headline)
        if let duration = episode.duration {
          Text("\(Int(duration / 60)) " + localized("min")).font(.caption).foregroundStyle(
            CinemaSecondary())
        }
        if !episode.overview.isEmpty { Text(episode.overview).foregroundStyle(CinemaSecondary()) }
        if let record = model.library.history.first(where: { $0.item.key == episode.item.key }),
          record.duration > 0
        {
          ProgressView(value: min(1, record.position / record.duration))
        }
        if model.library.watched.contains(episode.item.key) {
          Label(localized("Watched"), systemImage: "checkmark.circle.fill").foregroundStyle(
            Cinema.accentText)
        }
        ViewThatFits(in: .horizontal) {
          HStack { episodeActions(episode) }
          VStack(alignment: .leading) { episodeActions(episode) }
        }
      }.padding(16).background(Cinema.panel, in: RoundedRectangle(cornerRadius: 16))
        .accessibilityIdentifier("episode.\(episode.item.id)")
    }
  }
  @ViewBuilder private func episodeActions(_ episode: Episode) -> some View {
    Button(
      localized(model.library.position(episode.item) > 0 ? "Resume episode" : "Play episode"),
      systemImage: "play.fill"
    ) { model.play(episode.item, details: details) }.buttonStyle(CinemaPrimaryButtonStyle())
    Button(localized("From beginning")) {
      model.play(episode.item, details: details, restart: true)
    }
    .buttonStyle(.bordered)
    Button(
      model.library.watched.contains(episode.item.key)
        ? localized("Mark unwatched") : localized("Mark watched")
    ) { model.mark([episode.item], watched: !model.library.watched.contains(episode.item.key)) }
    .buttonStyle(.bordered)
  }
  private func load() async {
    loading = true
    error = nil
    defer { loading = false }
    guard item.kind != .live else { return }
    do {
      details = try await model.details(item)
      let preferred = model.library.seasons[item.id] ?? continuation?.season ?? seasons.first
      selectedSeason = preferred.flatMap { seasons.contains($0) ? $0 : nil } ?? seasons.first
      if let c = model.credentials {
        var filter = CatalogFilter()
        filter.category = item.categoryID
        related = try await model.catalogStore.page(
          scope: c.scope, kind: item.kind, filter: filter, limit: 13
        ).items.filter { $0.key != item.key }
      }
    } catch is CancellationError {} catch { self.error = friendly(error) }
  }
}
struct HistoryView: View {
  @EnvironmentObject private var model: AppModel
  @State private var clear = false
  var body: some View {
    List {
      if model.library.history.isEmpty {
        ContentUnavailableView(localized("No watch history"), systemImage: "clock")
      }
      ForEach(model.library.history, id: \.item.key) { record in
        NavigationLink {
          DetailsView(item: model.parent(record.item))
        } label: {
          VStack(alignment: .leading, spacing: 6) {
            Text(record.item.name).font(.headline)
            Text(
              record.completed
                ? localized("Watched") : "\(Int(record.position / 60)) " + localized("min")
            ).font(
              .caption
            ).foregroundStyle(CinemaSecondary())
            if record.duration > 0 {
              ProgressView(value: min(1, record.position / record.duration))
            }
          }.padding(.vertical, 6)
        }.swipeActions {
          Button(localized("Remove"), role: .destructive) { model.removeHistory(record.item) }
        }
      }
    }.navigationTitle(localized("Watch history"))
      .toolbar {
        Button(localized("Clear history")) { clear = true }.disabled(model.library.history.isEmpty)
      }
      .confirmationDialog(
        localized("Clear watch history?"), isPresented: $clear, titleVisibility: .visible
      ) {
        Button(localized("Clear history"), role: .destructive) { model.clearHistory() }
      } message: {
        Text(
          localized(
            "This also resets saved progress for titles in your recent history. Favorites stay saved."
          ))
      }
  }
}
