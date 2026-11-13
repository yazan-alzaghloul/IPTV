import IPTVCore
import SwiftUI

struct DiscoveryRail: Identifiable {
  let id: String
  let title: String
  let items: [CatalogItem]
}
struct DiscoveryView: View {
  @EnvironmentObject private var model: AppModel
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  let kind: ContentKind?
  @State private var rails: [DiscoveryRail] = []
  @State private var featured: [CatalogItem] = []
  @State private var featuredIndex = 0
  @State private var info: ContentDetails?
  private var hero: CatalogItem? {
    featured.isEmpty ? nil : featured[min(featuredIndex, featured.count - 1)]
  }
  var body: some View {
    ScrollView {
      LazyVStack(alignment: .leading, spacing: 24) {
        if let kind {
          ViewThatFits(in: .horizontal) {
            HStack {
              browseLink(kind)
              historyLink
            }
            VStack(alignment: .leading) {
              browseLink(kind)
              historyLink
            }
          }.padding(.horizontal, 24)
        }
        if let error = model.error {
          ErrorBanner(message: error, retry: { model.refresh() }).padding(.horizontal, 24)
        }
        if model.busy && featured.isEmpty {
          ProgressView(localized("Loading your library")).padding(24)
        }
        if let hero {
          FeaturedHero(item: hero, details: info)
            .id(hero.key)
            .task(id: hero.key) {
              info = nil
              if hero.kind != .live { info = try? await model.details(hero) }
            }
          if featured.count > 1 {
            HStack {
              Button(localized("Previous featured"), systemImage: "chevron.left") {
                featuredIndex = (featuredIndex - 1 + featured.count) % featured.count
              }
              Spacer()
              Text("\(min(featuredIndex + 1, featured.count)) / \(featured.count)").font(.caption)
                .foregroundStyle(CinemaSecondary())
              Spacer()
              Button(localized("Next featured"), systemImage: "chevron.right") {
                featuredIndex = (featuredIndex + 1) % featured.count
              }
            }.buttonStyle(.bordered).padding(.horizontal, 24)
          }
        } else if !model.busy {
          ContentUnavailableView(
            localized("No titles available"), systemImage: "film",
            description: Text(localized("Refresh to load content from your provider.")))
        }
        ForEach(rails) { MediaRail(title: $0.title, items: $0.items) }
        Button(localized("Refresh library")) { model.refresh() }.buttonStyle(.bordered).padding(
          .horizontal, 24)
      }.padding(.vertical, 16)
    }.background(Cinema.background)
      .refreshable { model.refresh() }
      .task(
        id:
          "\(model.revision)-\(kind?.rawValue ?? "home")-\(model.library.history.count)-\(model.library.favorites.map(\.key).joined())-\(model.appearance.hiddenRows.sorted())"
      ) { await load() }
      .task(
        id:
          "\(model.appearance.motion)-\(model.appearance.heroRotation)-\(reduceMotion)-\(featured.count)"
      ) {
        guard model.appearance.motion, model.appearance.heroRotation, !reduceMotion else { return }
        while !Task.isCancelled {
          do {
            try await Task.sleep(for: .seconds(12))
            if featured.count > 1 {
              withAnimation(.easeInOut(duration: 0.3)) {
                featuredIndex = (featuredIndex + 1) % featured.count
              }
            }
          } catch { return }
        }
      }
  }
  private func browseLink(_ kind: ContentKind) -> some View {
    NavigationLink(localized("Browse") + " " + localized(kind.label)) {
      BrowserView(mode: .catalog(kind))
    }
    .buttonStyle(CinemaPrimaryButtonStyle())
  }
  private var historyLink: some View {
    NavigationLink(localized("Watch history")) { HistoryView() }.buttonStyle(.bordered)
  }
  private func load() async {
    guard let c = model.credentials else { return }
    var result: [DiscoveryRail] = []
    func add(_ id: String, _ title: String, _ items: [CatalogItem], hidden: String? = nil) {
      if !items.isEmpty, !(kind == nil && model.appearance.hiddenRows.contains(hidden ?? title)) {
        result.append(.init(id: id, title: title, items: Array(items.prefix(20))))
      }
    }
    let kinds = kind.map { [$0] } ?? [.movie, .series, .live]
    var candidates: [CatalogItem] = []
    for k in kinds {
      let entries =
        (try? await model.catalogStore.page(scope: c.scope, kind: k, limit: 100).items) ?? []
      candidates += entries
    }
    let history = model.library.history.filter {
      !$0.completed && $0.position > 0 && $0.item.kind != .live
        && (kind == nil || $0.item.kind == kind)
    }
    var seen = Set<String>()
    let continuing = history.map { model.parent($0.item) }.filter { seen.insert($0.key).inserted }
    add("continue", "Continue watching", continuing)
    if kind == nil {
      add(
        "recent-live", "Recently watched channels",
        model.library.history.filter { $0.item.kind == .live }.map(\.item))
    }
    add(
      "favorites", kind == nil ? "Favorites" : "My watchlist",
      model.library.favorites.filter { kind == nil || $0.kind == kind })
    for k in kinds {
      add(
        "kind-\(k.rawValue)", kind == nil ? k.label : "Recently added",
        candidates.filter { $0.kind == k }, hidden: k.label)
    }
    let recommendations = MediaRules.recommendations(
      candidates: candidates, history: model.library.history, favorites: model.library.favorites)
    add("recommended", "Recommended from your viewing", recommendations)
    if let kind {
      var top = CatalogFilter()
      top.sort = .rating
      top.ratedOnly = true
      if let entries = try? await model.catalogStore.page(
        scope: c.scope, kind: kind, filter: top, limit: 12
      ).items {
        add("ratings", "Top rated by your provider", entries)
      }
      for group in (model.categories[kind] ?? []).prefix(8) {
        var filter = CatalogFilter()
        filter.category = group.id
        if let entries = try? await model.catalogStore.page(
          scope: c.scope, kind: kind, filter: filter, limit: 12
        ).items {
          add("category-\(group.id)", group.name, entries)
        }
      }
    } else {
      let genres = Dictionary(
        grouping: candidates.filter { $0.kind != .live }.flatMap { item in
          MediaRules.genreParts(item.genre).map { ($0, item) }
        }, by: \.0)
      for (genre, entries) in genres.sorted(by: { $0.value.count > $1.value.count }).prefix(6) {
        add("genre-\(genre)", genre.capitalized, entries.map(\.1))
      }
    }
    guard !Task.isCancelled else { return }
    featured = Array(candidates.prefix(8))
    rails = result
  }
}
struct FeaturedHero: View {
  @EnvironmentObject private var model: AppModel
  let item: CatalogItem
  var details: ContentDetails?
  var showDetails = true
  var body: some View {
    VStack(alignment: .leading, spacing: 14) {
      ZStack(alignment: .bottomLeading) {
        Artwork(item: heroArtwork, backdrop: true).frame(height: 250)
        LinearGradient(colors: [.clear, .black.opacity(0.9)], startPoint: .top, endPoint: .bottom)
          .allowsHitTesting(false)
        VStack(alignment: .leading, spacing: 8) {
          Text(localized(item.kind.label).uppercased()).font(.caption.bold()).foregroundStyle(
            Cinema.cyan)
          Text(item.name).font(.largeTitle.bold()).foregroundStyle(.white).accessibilityAddTraits(
            .isHeader)
          Text(
            [item.year, details?.genre ?? item.genre, details?.duration ?? ""].filter {
              !$0.isEmpty
            }.joined(separator: " · ")
          ).font(.subheadline).foregroundStyle(.white.opacity(0.85))
        }.padding(20)
      }.clipShape(RoundedRectangle(cornerRadius: 16))
      if let overview = details?.overview, !overview.isEmpty {
        Text(overview).lineLimit(3).foregroundStyle(CinemaSecondary())
      }
      ViewThatFits(in: .horizontal) {
        HStack { actions }
        VStack(alignment: .leading) { actions }
      }
    }.padding(.horizontal, 24)
  }
  private var heroArtwork: CatalogItem {
    var result = item
    if let backdrop = details?.backdrop, !backdrop.isEmpty { result.backdrop = backdrop }
    return result
  }
  @ViewBuilder private var actions: some View {
    if item.kind == .series {
      if let continuation = MediaRules.continuation(
        seriesID: item.id, episodes: details?.episodes ?? [], history: model.library.history,
        watched: model.library.watched)
      {
        Button(localized("Continue series"), systemImage: "play.fill") {
          model.play(continuation.item, details: details)
        }.buttonStyle(CinemaPrimaryButtonStyle())
      }
    } else {
      Button(
        localized(model.library.position(item) > 0 ? "Resume watching" : "Play now"),
        systemImage: "play.fill"
      ) { model.play(item, details: details) }.buttonStyle(CinemaPrimaryButtonStyle())
    }
    if showDetails {
      NavigationLink(localized("Details")) { DetailsView(item: item) }.buttonStyle(.bordered)
    }
    Button(
      model.isFavorite(item) ? localized("Saved") : localized("Watchlist"),
      systemImage: model.isFavorite(item) ? "heart.fill" : "heart"
    ) { model.toggle(item) }.buttonStyle(.bordered)
  }
}
