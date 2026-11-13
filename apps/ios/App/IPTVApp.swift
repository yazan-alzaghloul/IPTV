import SwiftUI

@main struct IPTVApp: App {
  private let result: Result<AppModel, Error>
  init() { result = Result { try AppModel.production() } }
  var body: some Scene {
    WindowGroup {
      switch result {
      case .success(let model): RootView(model: model)
      case .failure:
        ContentUnavailableView(
          localized("Storage unavailable"), systemImage: "externaldrive.badge.exclamationmark",
          description: Text(localized("Check available device storage and reopen IPTV.")))
      }
    }
  }
}
struct RootView: View {
  @StateObject var model: AppModel
  @Environment(\.scenePhase) private var phase
  @Environment(\.dynamicTypeSize) private var systemTypeSize
  var body: some View {
    Group {
      if model.restoring {
        ProgressView(localized("Loading"))
      } else if model.credentials == nil {
        LoginView()
      } else {
        AppShell()
      }
    }
    .environmentObject(model)
    .tint(
      model.appearance.accent == "cyan"
        ? Color(hex: model.appearance.theme == "light" ? 0x006875 : 0x38BDF8)
        : Color(hex: model.appearance.theme == "light" ? 0x6D28D9 : 0xC4B5FD)
    )
    .preferredColorScheme(model.appearance.theme == "light" ? .light : .dark)
    .controlSize(.large)
    .environment(\.cinemaHighContrast, model.appearance.highContrast)
    .environment(\.dynamicTypeSize, scaledTypeSize)
    .task { await model.restore() }
    .onChange(of: phase) { _, new in if new == .active { model.refresh(force: false) } }
    .fullScreenCover(item: $model.playback) { request in
      PlayerScreen(request: request, app: model).environmentObject(model)
    }
  }
  private var scaledTypeSize: DynamicTypeSize {
    let sizes: [DynamicTypeSize] = [
      .xSmall, .small, .medium, .large, .xLarge, .xxLarge, .xxxLarge, .accessibility1,
      .accessibility2, .accessibility3, .accessibility4, .accessibility5,
    ]
    let index = sizes.firstIndex(of: systemTypeSize) ?? 3
    let offset = Int(((model.appearance.fontScale - 1) * 8).rounded())
    return sizes[min(sizes.count - 1, max(0, index + offset))]
  }
}
struct AppShell: View {
  @EnvironmentObject private var model: AppModel
  @Environment(\.horizontalSizeClass) private var size
  @State private var selection: Section? = .home
  var body: some View {
    Group {
      if size == .regular {
        NavigationSplitView {
          List(Section.allCases, selection: $selection) { section in
            Label(localized(section.title), systemImage: section.icon).tag(section)
          }
          .navigationTitle("IPTV")
          .onChange(of: selection) { _, value in if let value { model.section = value } }
          .onChange(of: model.section) { _, value in selection = value }
        } detail: {
          NavigationStack { sectionView(model.section) }
        }
        .onAppear { selection = model.section }
      } else {
        TabView(selection: $model.section) {
          ForEach([Section.home, .live, .movies, .series]) { section in
            NavigationStack { sectionView(section) }.tabItem {
              Label(localized(section.title), systemImage: section.icon)
            }.tag(section)
          }
          NavigationStack { MoreView() }.tabItem {
            Label(localized("More"), systemImage: "ellipsis.circle")
          }.tag(Section.settings)
        }
      }
    }
  }
  @ViewBuilder private func sectionView(_ section: Section) -> some View {
    Group {
      switch section {
      case .home: DiscoveryView(kind: nil)
      case .movies: DiscoveryView(kind: .movie)
      case .series: DiscoveryView(kind: .series)
      case .live: LiveView()
      case .favorites: BrowserView(mode: .favorites)
      case .search: BrowserView(mode: .search)
      case .settings: SettingsView()
      }
    }.navigationTitle(localized(section.title))
      .toolbar {
        if section != .search {
          ToolbarItem(placement: .topBarTrailing) {
            NavigationLink {
              BrowserView(mode: .search)
            } label: {
              Label(localized("Search"), systemImage: "magnifyingglass")
            }
          }
        }
      }
      .background(Cinema.background)
  }
}
struct MoreView: View {
  var body: some View {
    List {
      NavigationLink {
        BrowserView(mode: .favorites)
      } label: {
        Label(localized("Favorites"), systemImage: "heart.fill")
      }
      NavigationLink {
        BrowserView(mode: .search)
      } label: {
        Label(localized("Search"), systemImage: "magnifyingglass")
      }
      NavigationLink {
        HistoryView()
      } label: {
        Label(localized("Watch history"), systemImage: "clock.fill")
      }
      NavigationLink {
        SettingsView()
      } label: {
        Label(localized("Settings"), systemImage: "gearshape.fill")
      }
    }.navigationTitle(localized("More"))
  }
}
