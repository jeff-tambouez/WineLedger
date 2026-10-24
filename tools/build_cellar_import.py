#!/usr/bin/env python3
"""Builds tools/cellar_import.csv, a cellar file the app imports (Settings > Import).

Source: the "Inventaire des Vins" sheet (producer, wine, vintage, style, country / region).
Cleaning done here:
  - text in brackets that is not part of the identity (estate, club, grape, bottler) goes to Notes;
  - the same wine written two ways is given one producer and one name, so it stays one wine
    with several vintages in the cellar;
  - country in English and Italian / German / Spanish regions in their local name, like the
    Veronelli guide, so filters group them;
  - grapes only where the appellation or the name makes them certain.
Price, supplier, purchase date and drinking window are left empty: the sheet does not say.

Usage: python3 tools/build_cellar_import.py
"""
import csv
import sys
from pathlib import Path

HEADER = [
    "Producer", "Wine", "Vintage", "Country", "Region", "Style", "Grapes", "Format", "Quantity",
    "Place", "Price", "Drink from", "Drink by", "Bought from", "Purchase date", "Notes",
]

# producer, wine, vintage, style, country, region, grapes, notes
ROWS = [
    ("Cantina Luigi Pira", "Barbera d'Alba Superiore", 2023, "Red", "Italy", "Piemonte", "Barbera", "Az. Agr. Pira Luigi di Gianpaolo Pira"),
    ("Tenuta La Meridiana", "Rocket Roll - Barbera d'Asti Superiore", 2021, "Red", "Italy", "Piemonte", "Barbera", "EUMETSAT & ESOC Wine Club"),
    ("Domaine Jean Vullien et Fils", "Vin de Savoie - St Jean de la Porte - Mondeuse Réserve du Château", 2023, "Red", "France", "Savoie", "Mondeuse", "Château St Philippe"),
    ("Grand Enclos du Château de Cérons", "Cérons", 2018, "Dessert", "France", "Bordeaux", "", "Grand Vin de Bordeaux. Blanc moelleux."),
    ("Château Mont-Redon", "Châteauneuf-du-Pape", 2020, "Red", "France", "Vallée du Rhône", "", "Famille Abeille-Fabre"),
    ("Ferraton Père & Fils", "Crozes-Hermitage - Calendes", 2022, "Red", "France", "Vallée du Rhône", "Syrah", ""),
    ("Cenatiempo", "Ischia D.O.C. Biancolella", None, "White", "Italy", "Campania", "Biancolella", "Ischia"),
    ("Château des Fines Roches", "Châteauneuf-du-Pape", 2021, "Red", "France", "Vallée du Rhône", "", "S.A.S. Vignobles Mousset - Barrot"),
    ("Famille Masse", "Givry 1er Cru - En Veau", 2023, "Red", "France", "Bourgogne", "Pinot Noir", "Barizey"),
    ("Château Chantalouette", "Pomerol", 2019, "Red", "France", "Bordeaux", "", "Château de Sales"),
    ("Château de la Jaubertie", "Mirabelle", 2022, "White", "France", "Bergerac", "", ""),
    ("Château Monestier La Tour", "Grand Vin", 2018, "Red", "France", "Bergerac", "", ""),
    ("Château de Tiregand", "Pécharmant", 2023, "Red", "France", "Bergerac", "", "Pécharmant"),
    ("Château Tour des Gendres", "Swing", None, "Red", "France", "Bergerac", "", ""),
    ("Bodega Gil Berzal", "Tradición - Rioja Alavesa", 2022, "Red", "Spain", "La Rioja", "", ""),
    ("Domaine Monblanc", "Métamorphose", None, "Red", "France", "Madiran", "", ""),
    ("Olivier Pouchoulou", "Xut - Irouléguy", 2024, "Red", "France", "Sud-Ouest", "", "Pays Basque"),
    ("Château Kirwan", "Margaux Grand Cru Classé", 2020, "Red", "France", "Bordeaux", "", "Schröder & Schÿler"),
    ("Bodega Chacra", "Mainqué Chardonnay", None, "White", "Argentina", "Patagonia", "Chardonnay", "Jean-Marc Roulot & Piero Incisa della Rocchetta"),
    ("Montes Alpha", "Special Cuvée Chardonnay", None, "White", "Chile", "Zapallar", "Chardonnay", "Zapallar Vineyard"),
    ("Vides y Vinos Ossian", "Ossian - Viñas Viejas de Segovia", None, "White", "Spain", "Castilla y León", "Verdejo", "Nieva (Segovia)"),
    ("António Madeira", "Vinhas Velhas Branco", 2016, "White", "Portugal", "Dão", "", "Dão Vinhas Velhas, sous-région Serra da Estrela"),
    ("Tenuta delle Terre Nere", "Etna Bianco - Calderara Sottana", None, "White", "Italy", "Sicilia", "Carricante", "Etna"),
    ("Weingut Tement", "Ried Grassnitzberg Riff Sauvignon Blanc", None, "White", "Austria", "Südsteiermark", "Sauvignon Blanc", "Südsteiermark DAC"),
    ("Lions de Batailley", "Pauillac", 2018, "Red", "France", "Bordeaux", "", ""),
    ("Château Ferrande", "Graves", 2020, "Red", "France", "Bordeaux", "", ""),
    ("Château La Grâce Dieu Les Menuts", "Saint-Émilion Grand Cru", 2018, "Red", "France", "Bordeaux", "", ""),
    ("Château de Cérons", "Cérons", 2020, "Dessert", "France", "Bordeaux", "", "Blanc moelleux"),
    ("Château Tour des Termes", "Saint-Estèphe", 2020, "Red", "France", "Bordeaux", "", ""),
    ("Château Léoville Poyferré", "Pavillon de Léoville Poyferré - Saint-Julien", 2018, "Red", "France", "Bordeaux", "", ""),
    ("Château Kirwan", "Margaux Grand Cru Classé", 2019, "Red", "France", "Bordeaux", "", ""),
    ("Château Clinet", "Pomerol", 2014, "Red", "France", "Bordeaux", "", ""),
    ("De Fermo", "Don Carlino Pecorino d'Abruzzo Superiore", 2024, "White", "Italy", "Abruzzo", "Pecorino", ""),
    ("De Fermo", "Le Cince Cerasuolo d'Abruzzo Superiore", 2024, "Rosé", "Italy", "Abruzzo", "Montepulciano", ""),
    ("Barone Cornacchia", "Varano Trebbiano d'Abruzzo Macerato", 2022, "White", "Italy", "Abruzzo", "Trebbiano", "Orange / macerato"),
    ("Emidio Pepe", "Trebbiano d'Abruzzo", 2024, "White", "Italy", "Abruzzo", "Trebbiano", ""),
    ("Masciarelli", "Marina Cvetic Trebbiano d'Abruzzo Riserva", 2023, "White", "Italy", "Abruzzo", "Trebbiano", ""),
    ("De Fermo", "Prologo Montepulciano d'Abruzzo", 2021, "Red", "Italy", "Abruzzo", "Montepulciano", ""),
    ("Masciarelli", "Villa Gemma Montepulciano d'Abruzzo Riserva", 2019, "Red", "Italy", "Abruzzo", "Montepulciano", ""),
    ("Marcalberto", "Alta Langa Nature", 2022, "Sparkling", "Italy", "Piemonte", "", "Alta Langa"),
    ("Colombo", "Alta Langa Rosé", 2022, "Sparkling", "Italy", "Piemonte", "", "Alta Langa, effervescent rosé"),
    ("Carl Loewen", "Riesling Alte Reben Trocken", 2025, "White", "Germany", "Mosel", "Riesling", ""),
    ("Reinhold & Cornelia Schneider", "Weißer Burgunder Trio Spätlese Trocken", 2024, "White", "Germany", "Baden", "Pinot Blanc", ""),
    ("Château Suduiraut", "Lions de Suduiraut Blanc Sec", 2024, "White", "France", "Bordeaux", "", ""),
    ("Château Olivier", "Pessac-Léognan Blanc", 2018, "White", "France", "Bordeaux", "", ""),
    ("Bodega Chacra", "Mainqué Chardonnay", 2024, "White", "Argentina", "Patagonia", "Chardonnay", ""),
    ("Pieropan", "Soave Classico La Rocca DOC", 2022, "White", "Italy", "Veneto", "Garganega", ""),
    ("Montes Alpha", "Special Cuvée Chardonnay", 2023, "White", "Chile", "Zapallar", "Chardonnay", ""),
    ("Alheit Vineyards", "Hemelrand Vine Garden", 2024, "White", "South Africa", "Walker Bay", "", ""),
    ("Vides y Vinos Ossian", "Ossian - Viñas Viejas de Segovia", 2022, "White", "Spain", "Castilla y León", "Verdejo", "Castilla y León Verdejo IGP Old Vines"),
    ("Vins Condrieu", "Les Cercles d'Or", 2023, "White", "France", "Vallée du Rhône", "Viognier", "Condrieu"),
    ("António Madeira", "Vinhas Velhas Branco", 2023, "White", "Portugal", "Dão", "", ""),
    ("Au Bon Climat", "Hildegard Estate", 2023, "White", "United States", "California", "", "Santa Maria Valley"),
    ("Tenuta delle Terre Nere", "Etna Bianco - Calderara Sottana", 2024, "White", "Italy", "Sicilia", "Carricante", "Etna"),
    ("Salwey", "Sieben Winter", 2016, "White", "Germany", "Baden", "", ""),
    ("Weingut Tement", "Ried Grassnitzberg Riff Sauvignon Blanc", 2022, "White", "Austria", "Südsteiermark", "Sauvignon Blanc", "Südsteiermark DAC"),
]


def main() -> int:
    seen = set()
    for r in ROWS:
        key = (r[0], r[1], r[2])
        if key in seen:
            print("duplicate line:", key, file=sys.stderr)
            return 1
        seen.add(key)

    out = Path(__file__).with_name("cellar_import.csv")
    with out.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, delimiter=";", quoting=csv.QUOTE_ALL, lineterminator="\r\n")
        w.writerow(HEADER)
        for producer, wine, vintage, style, country, region, grapes, notes in ROWS:
            w.writerow([
                producer, wine, vintage if vintage else "", country, region, style, grapes,
                "0.75", 1, "", "", "", "", "", "", notes,
            ])
    wines = {(r[0], r[1]) for r in ROWS}
    print(f"{out}: {len(ROWS)} cellar lines, {len(wines)} distinct wines")
    return 0


if __name__ == "__main__":
    sys.exit(main())
