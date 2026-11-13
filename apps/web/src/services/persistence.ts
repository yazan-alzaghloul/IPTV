import { emptyLibrary, type Library } from "@rayan/shared-types";
let database: Promise<IDBDatabase> | undefined;
function db() {
  return (database ??= new Promise((resolve, reject) => {
    const r = indexedDB.open("rayan-tv", 1);
    r.onupgradeneeded = () => r.result.createObjectStore("accounts");
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => {
      database = undefined;
      reject(new Error("Browser storage is unavailable."));
    };
  }));
}
export async function loadLibrary(scope: string): Promise<Library> {
  const d = await db();
  return new Promise((resolve, reject) => {
    const r = d.transaction("accounts").objectStore("accounts").get(scope);
    r.onsuccess = () => resolve(r.result ?? emptyLibrary());
    r.onerror = () => reject(r.error);
  });
}
export async function saveLibrary(scope: string, value: Library | null) {
  const d = await db();
  return new Promise<void>((resolve, reject) => {
    const t = d.transaction("accounts", "readwrite");
    if (value) t.objectStore("accounts").put(value, scope);
    else t.objectStore("accounts").delete(scope);
    t.oncomplete = () => resolve();
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error);
  });
}
