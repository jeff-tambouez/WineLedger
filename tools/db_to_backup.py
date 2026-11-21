#!/usr/bin/env python3
"""Turns a rescued Wine Ledger database into a backup.zip the app can restore.

The app's "Save a copy of the database" button writes a ZIP with the raw SQLite files
(wineledger.db plus its -wal and -shm companions). That copy is readable by this script but
not by the app. This rebuilds it into the backup format that Settings > "Restore a backup"
accepts, so a cellar can be recovered even when the app can no longer open its own database.

Works on a database of any version the app has shipped (1 to 4): columns added along the way
are read when present and left out otherwise.

Photos are files, not rows, so they are not in a database copy. Pass --photos with a folder of
them to put them back; otherwise the wines keep their photo names and show the placeholder.

Usage:
    python3 tools/db_to_backup.py wine-ledger-database-2026-11-21.zip -o backup.zip
    python3 tools/db_to_backup.py /path/to/wineledger.db -o backup.zip --photos ./photos
"""
import argparse
import json
import os
import shutil
import sqlite3
import sys
import tempfile
import zipfile
from datetime import datetime, timezone
from pathlib import Path

DB_NAME = "wineledger.db"
SNAP = ["format", "price", "readyFrom", "drinkBy", "supplier", "purchaseDate"]


def columns(con, table):
    try:
        return {r[1] for r in con.execute(f"PRAGMA table_info({table})")}
    except sqlite3.DatabaseError:
        return set()


def rows(con, table, fields, snapshot=False):
    """Read `table`, keeping only the fields the database actually has."""
    have = columns(con, table)
    if not have:
        return []
    wanted = [f for f in fields if f in have]
    snap_cols = [c for c in SNAP if f"b_{c}" in have] if snapshot else []
    select = wanted + [f"b_{c}" for c in snap_cols]
    out = []
    for r in con.execute(f"SELECT {', '.join(select)} FROM {table}"):
        o = {f: r[i] for i, f in enumerate(wanted)}
        if snapshot:
            snap = {c: r[len(wanted) + i] for i, c in enumerate(snap_cols)}
            o["bottle"] = snap if any(v is not None for v in snap.values()) else None
        out.append(o)
    return out


def snapshot_json(con):
    data = {
        "app": "WineLedger",
        "version": 1,
        "exportedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "profiles": rows(con, "profiles", ["id", "name", "accent"]),
        "wines": rows(con, "wines", [
            "id", "wineKey", "producer", "name", "country", "region", "style", "grapes", "photo"]),
        "bottles": rows(con, "bottles", [
            "id", "wineId", "vintage", "format", "quantity", "price", "readyFrom", "drinkBy",
            "supplier", "purchaseDate", "notes", "photo", "addedAt"]),
        "tastings": rows(con, "tastings", [
            "id", "wineId", "profileId", "year", "score", "notes", "photo", "createdAt", "vintage"],
            snapshot=True),
        "openings": rows(con, "openings", [
            "id", "date", "producer", "wine", "vintage", "price", "score", "notes"], snapshot=True),
        "wishlist": rows(con, "wishlist", ["profileId", "guideKey"]),
        "invitations": rows(con, "invitations", [
            "id", "wineId", "profileId", "fromProfileId", "year", "vintage", "stage", "createdAt",
            "notes"], snapshot=True),
        "customGrapes": [r[0] for r in con.execute("SELECT name FROM custom_grapes")]
        if columns(con, "custom_grapes") else [],
    }
    if not data["profiles"]:
        raise SystemExit("This database has no profile: the app would refuse to restore it.")
    return data


def locate_db(source, workdir):
    """Copy the database and its companions into workdir so SQLite can replay the -wal log."""
    src = Path(source)
    if not src.exists():
        raise SystemExit(f"Not found: {src}")
    if src.is_file() and zipfile.is_zipfile(src):
        with zipfile.ZipFile(src) as z:
            names = [n for n in z.namelist() if Path(n).name.startswith(DB_NAME)]
            if not names:
                raise SystemExit(f"No {DB_NAME} inside {src}")
            for n in names:
                (workdir / Path(n).name).write_bytes(z.read(n))
        return workdir / DB_NAME
    files = [src] + [Path(str(src) + suffix) for suffix in ("-wal", "-shm")] if src.is_file() \
        else [src / DB_NAME, src / (DB_NAME + "-wal"), src / (DB_NAME + "-shm")]
    main = files[0]
    if not main.is_file():
        raise SystemExit(f"No {DB_NAME} at {main}")
    for f in files:
        if f.is_file():
            shutil.copy2(f, workdir / (DB_NAME + str(f)[len(str(main)):]))
    return workdir / DB_NAME


def main():
    ap = argparse.ArgumentParser(description="Rebuild a restorable backup.zip from a rescued database.")
    ap.add_argument("source", help="the rescue ZIP, a wineledger.db file, or a folder holding one")
    ap.add_argument("-o", "--out", default="backup.zip", help="backup file to write (default backup.zip)")
    ap.add_argument("--photos", help="folder of photo files to include")
    args = ap.parse_args()

    with tempfile.TemporaryDirectory() as tmp:
        db = locate_db(args.source, Path(tmp))
        # read-write on purpose: this is a copy, and SQLite needs to write to replay the -wal log.
        # Opening read-only fails outright when the log still needs recovery.
        con = sqlite3.connect(str(db))
        data = snapshot_json(con)
        con.close()

    with zipfile.ZipFile(args.out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("backup.json", json.dumps(data))
        if args.photos:
            folder = Path(args.photos)
            for f in sorted(folder.iterdir()) if folder.is_dir() else []:
                if f.is_file():
                    z.write(f, "photos/" + f.name)

    counts = ", ".join(
        f"{len(data[k])} {k}" for k in
        ("profiles", "wines", "bottles", "tastings", "openings", "wishlist", "invitations"))
    print(f"{args.out}: {counts}")
    print("Restore it with Settings > Backup > Restore a backup. It replaces everything on the phone.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
