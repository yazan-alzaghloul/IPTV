import CSQLite
import Foundation

/// Serialized SQLite access and indexed pages keep large provider catalogs off the UI thread.
public actor CatalogStore {
  private var db: OpaquePointer?
  private let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
  public init(url: URL) throws {
    try FileManager.default.createDirectory(
      at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    guard
      sqlite3_open_v2(
        url.path, &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil)
        == SQLITE_OK
    else { throw IPTVError.message("Could not open the catalog cache.") }
    let schema = """
      PRAGMA journal_mode=WAL;
      CREATE TABLE IF NOT EXISTS catalog(scope TEXT,kind TEXT,id TEXT,title TEXT,category TEXT,year TEXT,language TEXT,genre TEXT,added INTEGER,rating REAL,released TEXT,ordinal INTEGER,json BLOB,PRIMARY KEY(scope,kind,id));
      CREATE INDEX IF NOT EXISTS catalog_title ON catalog(scope,kind,title);
      CREATE INDEX IF NOT EXISTS catalog_added ON catalog(scope,kind,added DESC);
      CREATE INDEX IF NOT EXISTS catalog_category ON catalog(scope,kind,category);
      CREATE TABLE IF NOT EXISTS groups_cache(scope TEXT,kind TEXT,id TEXT,name TEXT,PRIMARY KEY(scope,kind,id));
      """
    guard sqlite3_exec(db, schema, nil, nil, nil) == SQLITE_OK else {
      sqlite3_close(db)
      throw IPTVError.message("Could not initialize the catalog cache.")
    }
    var directory = url.deletingLastPathComponent()
    var values = URLResourceValues()
    values.isExcludedFromBackup = true
    try directory.setResourceValues(values)
    #if os(iOS)
      try FileManager.default.setAttributes(
        [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
        ofItemAtPath: url.path)
    #endif
  }
  deinit { sqlite3_close(db) }
  private func execute(_ sql: String, _ args: [String] = []) throws {
    let statement = try prepare(sql, args)
    defer { sqlite3_finalize(statement) }
    guard sqlite3_step(statement) == SQLITE_DONE else {
      throw IPTVError.message("Could not update the catalog cache.")
    }
  }
  private func prepare(_ sql: String, _ args: [String]) throws -> OpaquePointer {
    var statement: OpaquePointer?
    guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK, let statement else {
      throw IPTVError.message("Could not query the catalog cache.")
    }
    for (index, value) in args.enumerated() {
      sqlite3_bind_text(statement, Int32(index + 1), value, -1, transient)
    }
    return statement
  }
  private func rows(_ sql: String, _ args: [String]) throws -> [[String]] {
    let statement = try prepare(sql, args)
    defer { sqlite3_finalize(statement) }
    var result: [[String]] = []
    while true {
      let status = sqlite3_step(statement)
      if status == SQLITE_DONE { return result }
      guard status == SQLITE_ROW else {
        throw IPTVError.message("Could not read the catalog cache.")
      }
      result.append(
        (0..<sqlite3_column_count(statement)).map { index in
          sqlite3_column_text(statement, index).map { String(cString: $0) } ?? ""
        })
    }
  }
  public func replace(
    scope: String, kind: ContentKind, items: [CatalogItem], categories: [Category]
  ) throws {
    try execute("BEGIN IMMEDIATE")
    do {
      try execute("DELETE FROM catalog WHERE scope=? AND kind=?", [scope, kind.rawValue])
      try execute("DELETE FROM groups_cache WHERE scope=? AND kind=?", [scope, kind.rawValue])
      let statement = try prepare(
        "INSERT OR REPLACE INTO catalog VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", [])
      defer { sqlite3_finalize(statement) }
      for (index, item) in items.enumerated() {
        try Task.checkCancellation()
        sqlite3_reset(statement)
        sqlite3_clear_bindings(statement)
        let json = String(decoding: try JSONEncoder().encode(item), as: UTF8.self)
        let args = [
          scope, kind.rawValue, item.id, item.name.lowercased(), item.categoryID, item.year,
          item.language, item.genre.lowercased(), String(item.added),
          String(Double(item.rating) ?? -1),
          item.releaseDate.isEmpty ? item.year : item.releaseDate, String(index), json,
        ]
        for (i, value) in args.enumerated() {
          sqlite3_bind_text(statement, Int32(i + 1), value, -1, transient)
        }
        guard sqlite3_step(statement) == SQLITE_DONE else {
          throw IPTVError.message("Could not cache the provider catalog.")
        }
      }
      for category in categories {
        try execute(
          "INSERT OR REPLACE INTO groups_cache VALUES(?,?,?,?)",
          [scope, kind.rawValue, category.id, category.name])
      }
      try execute("COMMIT")
    } catch {
      try? execute("ROLLBACK")
      throw error
    }
  }
  public func categories(scope: String, kind: ContentKind) throws -> [Category] {
    try rows(
      "SELECT id,name FROM groups_cache WHERE scope=? AND kind=? ORDER BY name",
      [scope, kind.rawValue]
    ).map { Category(id: $0[0], name: $0[1]) }
  }
  public func page(
    scope: String, kind: ContentKind, filter: CatalogFilter = CatalogFilter(), offset: Int = 0,
    limit: Int = 48
  ) throws -> CatalogPage {
    var args = [scope, kind.rawValue]
    var whereSQL = "scope=? AND kind=?"
    for word in filter.query.lowercased().prefix(240).split(whereSeparator: \.isWhitespace) {
      whereSQL += " AND (title LIKE ? ESCAPE '\\' OR genre LIKE ? ESCAPE '\\')"
      let token =
        "%"
        + word.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(
          of: "%", with: "\\%"
        ).replacingOccurrences(of: "_", with: "\\_") + "%"
      args += [token, token]
    }
    for (field, value) in [
      ("category", filter.category), ("year", filter.year), ("language", filter.language),
    ] where !value.isEmpty {
      whereSQL += " AND \(field)=?"
      args.append(value)
    }
    if !filter.genre.isEmpty {
      whereSQL += " AND genre LIKE ? ESCAPE '\\'"
      args.append(
        "%"
          + filter.genre.lowercased().replacingOccurrences(of: "\\", with: "\\\\")
          .replacingOccurrences(of: "%", with: "\\%").replacingOccurrences(of: "_", with: "\\_")
          + "%")
    }
    if filter.ratedOnly { whereSQL += " AND rating>0" }
    let order: String
    switch filter.sort {
    case .provider: order = "ordinal ASC"
    case .title: order = "title ASC,id ASC"
    case .added: order = "added DESC,title ASC,id ASC"
    case .release: order = "released DESC,title ASC,id ASC"
    case .rating: order = "rating DESC,title ASC,id ASC"
    }
    let total =
      Int(try rows("SELECT count(*) FROM catalog WHERE \(whereSQL)", args).first?.first ?? "0") ?? 0
    let start = max(0, offset)
    let count = min(100, max(1, limit))
    let items = try rows(
      "SELECT json FROM catalog WHERE \(whereSQL) ORDER BY \(order) LIMIT \(count) OFFSET \(start)",
      args
    ).map { try JSONDecoder().decode(CatalogItem.self, from: Data($0[0].utf8)) }
    return CatalogPage(items: items, total: total, offset: start)
  }
  public func find(scope: String, kind: ContentKind, id: String) throws -> CatalogItem? {
    guard
      let json = try rows(
        "SELECT json FROM catalog WHERE scope=? AND kind=? AND id=?", [scope, kind.rawValue, id]
      ).first?.first
    else { return nil }
    return try JSONDecoder().decode(CatalogItem.self, from: Data(json.utf8))
  }
  public func facets(scope: String, kind: ContentKind) throws -> CatalogFacets {
    var result = CatalogFacets()
    func values(_ column: String) throws -> [String] {
      try rows(
        "SELECT DISTINCT \(column) FROM catalog WHERE scope=? AND kind=? AND \(column)<>'' ORDER BY \(column) DESC LIMIT 100",
        [scope, kind.rawValue]
      ).map { $0[0] }
    }
    result.years = try values("year")
    result.languages = try values("language")
    result.genres = Array(Set(try values("genre").flatMap { MediaRules.genreParts($0) })).sorted()
    result.hasRatings =
      !(try rows(
        "SELECT 1 FROM catalog WHERE scope=? AND kind=? AND rating>0 LIMIT 1",
        [scope, kind.rawValue])).isEmpty
    return result
  }
  public func clear(scope: String) throws {
    try execute("BEGIN IMMEDIATE")
    do {
      try execute("DELETE FROM catalog WHERE scope=?", [scope])
      try execute("DELETE FROM groups_cache WHERE scope=?", [scope])
      try execute("COMMIT")
    } catch {
      try? execute("ROLLBACK")
      throw error
    }
  }
}
