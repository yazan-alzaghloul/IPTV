import IPTVCore
import SwiftUI

enum BrowserMode: Hashable {
  case catalog(ContentKind)
  case favorites, search
  var title: String {
    switch self {
    case .catalog(let kind): return kind.label
    case .favorites: return "Favorites"
    case .search: return "Search"
    }
  }
}
struct BrowserView: View {
  @EnvironmentObject private var model: AppModel
  let mode: BrowserMode
  @State private var filter = CatalogFilter()
  @State private var kindFilter: ContentKind?
  @State private var page = 0
  @State private var total = 0
  @State private var items: [CatalogItem] = []
  @State private var facets = CatalogFacets()
  @State private var loading = false
  @State private var list = false
  @State private var hasNext = false
  @State private var error: String?
  @StateObject private var speech = VoiceSearch()
  private var stateKey: String { String(describing: mode) }
  private var currentKind: ContentKind? {
    if case .catalog(let kind) = mode { return kind }
    return kindFilter
  }
  var body: some View {
    VStack(spacing: 12) {
      HStack {
        TextField(localized("Search your library"), text: $filter.query).textFieldStyle(
          .roundedBorder
        )
        .submitLabel(.search).onSubmit { model.rememberSearch(filter.query) }
        .accessibilityIdentifier("catalog.search")
        Button {
          speech.isListening ? speech.stop() : speech.start()
        } label: {
          Image(systemName: speech.isListening ? "stop.circle.fill" : "mic.fill")
        }.frame(width: 44, height: 44).accessibilityLabel(
          localized(speech.isListening ? "Stop voice search" : "Voice search"))
        Button {
          list.toggle()
        } label: {
          Label(
            localized(list ? "Grid view" : "List view"),
            systemImage: list ? "square.grid.2x2" : "list.bullet")
        }.labelStyle(.iconOnly).frame(minWidth: 44, minHeight: 44)
      }.padding(.horizontal, 24)
      if let message = speech.error { Text(message).font(.footnote).foregroundStyle(.red) }
      ScrollView(.horizontal) {
        HStack {
          if case .catalog = mode {
          } else {
            Picker(localized("Content"), selection: $kindFilter) {
              Text(localized("All")).tag(ContentKind?.none)
              ForEach(ContentKind.allCases, id: \.self) {
                Text(localized($0.label)).tag(Optional($0))
              }
            }.pickerStyle(.menu)
          }
          if let kind = currentKind, !(model.categories[kind] ?? []).isEmpty {
            Picker(localized("Category"), selection: $filter.category) {
              Text(localized("All categories")).tag("")
              ForEach(model.categories[kind] ?? []) { Text($0.name).tag($0.id) }
            }
          }
          facetPicker("Genre", values: facets.genres, value: $filter.genre)
          facetPicker("Year", values: facets.years, value: $filter.year)
          facetPicker("Language", values: facets.languages, value: $filter.language)
          Picker(localized("Sort"), selection: $filter.sort) {
            ForEach(CatalogSort.allCases, id: \.self) { Text(localized($0.rawValue)).tag($0) }
          }
          if facets.hasRatings {
            Toggle(localized("Rated only"), isOn: $filter.ratedOnly).toggleStyle(.button)
          }
          Button(localized("Reset filters")) {
            filter = CatalogFilter()
            kindFilter = nil
          }
        }.buttonStyle(.bordered).padding(.horizontal, 24)
      }.scrollIndicators(.hidden)
      if filter.query.isEmpty && !model.library.searches.isEmpty {
        ScrollView(.horizontal) {
          HStack {
            ForEach(model.library.searches, id: \.self) { query in
              Button(query) { filter.query = query }
            }
          }.padding(.horizontal, 24)
        }.buttonStyle(.bordered)
      }
      if let error = error ?? model.error {
        ErrorBanner(
          message: error,
          retry: {
            model.refresh()
            Task { await load() }
          }
        ).padding(.horizontal, 24)
      }
      if loading { ProgressView(localized("Loading")) }
      HStack {
        Button(localized("Previous page")) { page -= 1 }.disabled(page == 0)
        Spacer()
        Text("\(page + 1) · \(total) " + localized("titles")).font(.caption).foregroundStyle(
          CinemaSecondary())
        Spacer()
        Button(localized("Next page")) { page += 1 }.disabled(!hasNext)
      }.buttonStyle(.bordered).padding(.horizontal, 24)
      if items.isEmpty && !loading {
        ContentUnavailableView(
          localized(
            mode == .favorites && filter.query.isEmpty
              ? "Keep your favorites here" : "No matching titles"), systemImage: "magnifyingglass",
          description: Text(localized("Try another filter or refresh your provider.")))
      } else {
        ScrollView {
          if list {
            LazyVStack(spacing: 14) {
              ForEach(items, id: \.key) { item in
                NavigationLink {
                  DetailsView(item: model.parent(item))
                } label: {
                  HStack(alignment: .top, spacing: 16) {
                    Artwork(item: item).frame(width: 80, height: 110)
                    VStack(alignment: .leading, spacing: 8) {
                      Text(item.name).font(.headline)
                      Text(
                        [localized(item.kind.label), item.year, item.genre].filter { !$0.isEmpty }
                          .joined(
                            separator: " · ")
                      ).font(.subheadline).foregroundStyle(CinemaSecondary())
                    }
                    Spacer()
                  }.foregroundStyle(.primary).padding().background(
                    Cinema.panel, in: RoundedRectangle(cornerRadius: 12))
                }.buttonStyle(.plain).contextMenu {
                  Button(localized("Toggle favorite")) { model.toggle(item) }
                }
              }
            }.padding(.horizontal, 24)
          } else {
            LazyVGrid(
              columns: [GridItem(.adaptive(minimum: 140, maximum: 220), spacing: 14)],
              alignment: .leading, spacing: 20
            ) {
              ForEach(items, id: \.key) { MediaCard(item: $0) }
            }.padding(.horizontal, 24)
          }
        }.id(page).refreshable { model.refresh() }
      }
    }.padding(.vertical, 12).background(Cinema.background).navigationTitle(localized(mode.title))
      .task(
        id:
          "\(filter)-\(page)-\(currentKind?.rawValue ?? "all")-\(model.revision)-\(model.library.favorites.map(\.key))"
      ) { await load() }
      .onChange(of: filter) { _, _ in page = 0 }
      .onChange(of: kindFilter) { _, _ in
        page = 0
        filter.category = ""
      }
      .onChange(of: speech.transcript) { _, value in
        if !value.isEmpty { filter.query = String(value.prefix(240)) }
      }
      .onChange(of: speech.isListening) { _, value in
        if !value { model.rememberSearch(filter.query) }
      }
      .onAppear {
        filter = model.browserFilters[stateKey] ?? CatalogFilter()
        page = model.browserPages[stateKey] ?? 0
        list = model.browserLists[stateKey] ?? false
        kindFilter = model.browserKinds[stateKey]
      }
      .onDisappear {
        speech.stop()
        model.browserFilters[stateKey] = filter
        model.browserPages[stateKey] = page
        model.browserLists[stateKey] = list
        model.browserKinds[stateKey] = kindFilter
      }
  }
  @ViewBuilder private func facetPicker(_ title: String, values: [String], value: Binding<String>)
    -> some View
  {
    if !values.isEmpty {
      Picker(localized(title), selection: value) {
        Text(localized(title) + ": " + localized("All")).tag("")
        ForEach(values, id: \.self) { Text($0).tag($0) }
      }
    }
  }
  private func load() async {
    guard let c = model.credentials else { return }
    loading = true
    error = nil
    do {
      if !filter.query.isEmpty { try await Task.sleep(for: .milliseconds(180)) }
      let kinds = currentKind.map { [$0] } ?? ContentKind.allCases
      var all: [CatalogItem] = []
      var count = 0
      var next = false
      var years = Set<String>()
      var languages = Set<String>()
      var genres = Set<String>()
      var ratings = false
      if mode == .favorites {
        let words = filter.query.lowercased().split(whereSeparator: \.isWhitespace)
        all = model.library.favorites.filter { item in
          (currentKind == nil || item.kind == currentKind)
            && (filter.category.isEmpty || item.categoryID == filter.category)
            && (filter.year.isEmpty || item.year == filter.year)
            && (filter.language.isEmpty || item.language == filter.language)
            && (filter.genre.isEmpty || item.genre.lowercased().contains(filter.genre.lowercased()))
            && (!filter.ratedOnly || (Double(item.rating) ?? 0) > 0)
            && words.allSatisfy { (item.name + " " + item.genre).lowercased().contains($0) }
        }
        switch filter.sort {
        case .title:
          all.sort { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        case .added: all.sort { $0.added > $1.added }
        case .release: all.sort { $0.releaseDate > $1.releaseDate }
        case .rating: all.sort { (Double($0.rating) ?? -1) > (Double($1.rating) ?? -1) }
        case .provider: break
        }
        count = all.count
        next = (page + 1) * 48 < count
        all = Array(all.dropFirst(page * 48).prefix(48))
        for item in model.library.favorites {
          if !item.year.isEmpty { years.insert(item.year) }
          if !item.language.isEmpty { languages.insert(item.language) }
          genres.formUnion(MediaRules.genreParts(item.genre))
          ratings = ratings || (Double(item.rating) ?? 0) > 0
        }
      } else {
        // Universal search shows a page per content kind, preserving every match in large catalogs.
        for kind in kinds {
          let result = try await model.catalogStore.page(
            scope: c.scope, kind: kind, filter: filter, offset: page * 48)
          all += result.items
          count += result.total
          next = next || result.offset + result.items.count < result.total
          let f = try await model.catalogStore.facets(scope: c.scope, kind: kind)
          years.formUnion(f.years)
          languages.formUnion(f.languages)
          genres.formUnion(f.genres)
          ratings = ratings || f.hasRatings
        }
      }
      try Task.checkCancellation()
      var f = CatalogFacets()
      f.years = years.sorted(by: >)
      f.languages = languages.sorted()
      f.genres = genres.sorted()
      f.hasRatings = ratings
      facets = f
      items = all
      total = count
      hasNext = next
      loading = false
    } catch is CancellationError {} catch {
      self.error = friendly(error)
      loading = false
    }
  }
}
