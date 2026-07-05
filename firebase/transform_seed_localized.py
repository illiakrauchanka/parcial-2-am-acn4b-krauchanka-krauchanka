#!/usr/bin/env python3
"""
Injects `nameLatin` (INN, Latin) and `names: {es,en,uk,be,zh}` into every medicine entry of
app/src/main/assets/meds_seed.json. The legacy `name` field is kept as the Spanish
fallback (so old payloads still parse). Run idempotently: existing names/nameLatin fields
are overwritten with the curated values from the LANG dict in this script.

IDs are stable (they are the @PrimaryKey in Room), so we key the lookup table by id and
let the script fail loudly if a new id appears that wasn't given translations — forcing a
deliberate update instead of a silent fallback.

Usage:
    python3 firebase/transform_seed_localized.py
"""
import json, sys, pathlib

SEED = pathlib.Path("app/src/main/assets/meds_seed.json")

# id -> (latin, {es, en, uk, be, zh})
T = {
    # ---- AR ----
    "ar-01-tramadol":       ("Tramadolum",                             {"es":"Tramadol",   "en":"Tramadol",        "uk":"Трамадол",        "be":"Трамадол",          "zh":"曲马多"}),
    "ar-02-codeina":        ("Codeinum",                               {"es":"Codeína",    "en":"Codeine",         "uk":"Кодеїн",          "be":"Кодэін",            "zh":"可待因"}),
    "ar-03-metadona":       ("Methadonum",                             {"es":"Metadona",   "en":"Methadone",        "uk":"Метадон",         "be":"Метадон",           "zh":"美沙酮"}),
    "ar-04-diazepam":       ("Diazepamum",                             {"es":"Diazepam",   "en":"Diazepam",         "uk":"Диазепам",        "be":"Диазепам",          "zh":"地西泮"}),
    "ar-05-alprazolam":     ("Alprazolamum",                           {"es":"Alprazolam", "en":"Alprazolam",       "uk":"Алпразолам",      "be":"Алпразолам",        "zh":"阿普唑仑"}),
    "ar-06-clonazepam":     ("Clonazepamum",                           {"es":"Clonazepam", "en":"Clonazepam",       "uk":"Клоназепам",      "be":"Клоназепам",        "zh":"氯硝西泮"}),
    "ar-07-metilanfetamina":("Methamphetaminum",                        {"es":"Metilanfetamina","en":"Methamphetamine","uk":"Метамфетамін","be":"Метамфетамін","zh":"甲基苯丙胺"}),
    "ar-08-cocaina":        ("Cocainum",                               {"es":"Cocaína",    "en":"Cocaine",          "uk":"Кокаїн",          "be":"Кокаін",            "zh":"可卡因"}),
    "ar-09-lsd":            ("Lysergidum",                             {"es":"LSD",        "en":"LSD",             "uk":"ЛСД",             "be":"ЛСД",               "zh":"麦角酸二乙胺(LSD)"}),
    "ar-10-ketamina":       ("Ketaminum",                              {"es":"Ketamina",   "en":"Ketamine",         "uk":"Кетамін",         "be":"Кетамін",           "zh":"氯胺酮"}),
    # ---- BR ----
    "br-01-cannabis":       ("Cannabis",                               {"es":"Cannabis / Maconha","en":"Cannabis","uk":"Коноплі (канабіс)","be":"Канабіс",       "zh":"大麻"}),
    "br-02-metanfetamina":  ("Methamphetaminum",                        {"es":"Metanfetamina","en":"Methamphetamine","uk":"Метамфетамін","be":"Метамфетамін","zh":"甲基苯丙胺"}),
    "br-03-anfepramona":    ("Amfepramonum",                            {"es":"Anfepramona (Dietilpropion)","en":"Amfepramone (Diethylpropion)","uk":"Анфепрамон (діетілпропіон)","be":"Анфепрамон (дыетылпрапіён)","zh":"安非拉酮(二乙基丙酮)"}),
    "br-04-fenproporex":    ("Fenproporexum",                          {"es":"Fenproporex","en":"Fenproporex",      "uk":"Фенпропорекс",    "be":"Фенпропорекс",      "zh":"芬普雷司"}),
    "br-05-mdma":           ("Methylendioxymethamphetaminum",           {"es":"MDMA / Êxtase","en":"MDMA / Ecstasy","uk":"MDMA / екстазі","be":"MDMA / экстаз", "zh":"摇头丸/MDMA"}),
    "br-06-lorazepam":      ("Lorazepamum",                            {"es":"Lorazepam",  "en":"Lorazepam",        "uk":"Лоразепам",       "be":"Ларазэпам",          "zh":"劳拉西泮"}),
    "br-07-diazepam":       ("Diazepamum",                             {"es":"Diazepam",   "en":"Diazepam",         "uk":"Диазепам",        "be":"Диазепам",          "zh":"地西泮"}),
    "br-08-codeina":        ("Codeinum",                               {"es":"Codeína",    "en":"Codeine",          "uk":"Кодеїн",          "be":"Кодэін",            "zh":"可待因"}),
    "br-09-metadona":       ("Methadonum",                             {"es":"Metadona",   "en":"Methadone",        "uk":"Метадон",         "be":"Метадон",           "zh":"美沙酮"}),
    "br-10-pseudoefedrina": ("Pseudoephedrinum",                       {"es":"Pseudoefedrina","en":"Pseudoephedrine","uk":"Псевдоефедрин","be":"Псеўдаэфедрын", "zh":"伪麻黄碱"}),
    # ---- JP ----
    "jp-01-pseudoefedrina": ("Pseudoephedrinum",                       {"es":"Pseudoefedrina","en":"Pseudoephedrine","uk":"Псевдоефедрин","be":"Псеўдаэфедрын", "zh":"伪麻黄碱"}),
    "jp-02-codeina-+-cafeina":("Codeinum + Coffeinum",                  {"es":"Codeína + Cafeína","en":"Codeine + Caffeine","uk":"Кодеїн + Кофеїн","be":"Кодэін + Кафеін","zh":"可待因+咖啡因"}),
    "jp-03-metanfetamina":  ("Methamphetaminum",                        {"es":"Metanfetamina","en":"Methamphetamine","uk":"Метамфетамін","be":"Метамфетамін","zh":"甲基苯丙胺"}),
    "jp-04-cannabis":       ("Cannabis",                               {"es":"Cannabis",   "en":"Cannabis",         "uk":"Коноплі (канабіс)","be":"Канабіс",        "zh":"大麻"}),
    "jp-05-mdma-(extasis)": ("Methylendioxymethamphetaminum",           {"es":"MDMA (Éxtasis)","en":"MDMA (Ecstasy)","uk":"MDMA (екстазі)","be":"MDMA (экстаз)","zh":"摇头丸(MDMA)"}),
    "jp-06-lsd":            ("Lysergidum",                             {"es":"LSD",        "en":"LSD",             "uk":"ЛСД",             "be":"ЛСД",               "zh":"麦角酸二乙胺(LSD)"}),
    "jp-07-heroina":        ("Diacetylmorphinum",                       {"es":"Heroína",    "en":"Heroin",           "uk":"Героїн",          "be":"Гераін",            "zh":"海洛因"}),
    "jp-08-ketamina":       ("Ketaminum",                              {"es":"Ketamina",   "en":"Ketamine",         "uk":"Кетамін",         "be":"Кетамін",           "zh":"氯胺酮"}),
    "jp-09-adrenalina-(inyectable)":("Epinephrinum",                    {"es":"Adrenalina (inyectable)","en":"Epinephrine (injectable)","uk":"Адреналін (ін'єкційний)","be":"Адрэналін (ін'екцыйны)","zh":"肾上腺素(注射)"}),
    "jp-10-vicks-vapor-inhaler-(l-d":("Levomethamphetaminum",          {"es":"Vicks Vapor Inhaler (l-desoxiefedrina)","en":"Vicks Vapor Inhaler (levomethamphetamine)","uk":"Vicks Vapor Inhaler (l-дезоксиефедрин)","be":"Vicks Vapor Inhaler (l-дэзоксіэфедрын)","zh":"维克斯吸入剂(左旋去氧麻黄碱)"}),
    # ---- AE ----
    "ae-01-codeina":        ("Codeinum",                               {"es":"Codeína",    "en":"Codeine",          "uk":"Кодеїн",          "be":"Кодэін",            "zh":"可待因"}),
    "ae-02-tramadol":       ("Tramadolum",                             {"es":"Tramadol",   "en":"Tramadol",         "uk":"Трамадол",        "be":"Трамадол",          "zh":"曲马多"}),
    "ae-03-pseudoefedrina": ("Pseudoephedrinum",                       {"es":"Pseudoefedrina","en":"Pseudoephedrine","uk":"Псевдоефедрин","be":"Псеўдаэфедрын", "zh":"伪麻黄碱"}),
    "ae-04-diazepam":       ("Diazepamum",                             {"es":"Diazepam",   "en":"Diazepam",         "uk":"Диазепам",        "be":"Диазепам",          "zh":"地西泮"}),
    "ae-05-cannabis":       ("Cannabis",                               {"es":"Cannabis",   "en":"Cannabis",         "uk":"Коноплі (канабіс)","be":"Канабіс",        "zh":"大麻"}),
    "ae-06-khat":           ("Cathinum",                               {"es":"Khat",       "en":"Khat",             "uk":"Кат (чат)",       "be":"Кат",               "zh":"恰特草"}),
    "ae-07-anfetaminas-(adderall)":("Amfetaminum",                      {"es":"Anfetaminas (Adderall)","en":"Amphetamines (Adderall)","uk":"Амфетаміни (Adderall)","be":"Амфетаміны (Adderall)","zh":"苯丙胺类(阿得拉)"}),
    "ae-08-metadona":       ("Methadonum",                             {"es":"Metadona",   "en":"Methadone",        "uk":"Метадон",         "be":"Метадон",           "zh":"美沙酮"}),
    "ae-09-melatonina":     ("Melatoninum",                            {"es":"Melatonina", "en":"Melatonin",        "uk":"Мелатонін",       "be":"Мелатанін",         "zh":"褪黑素"}),
    "ae-10-kava":           ("Kava",                                   {"es":"Kava",       "en":"Kava",             "uk":"Кава",            "be":"Кава",              "zh":"卡瓦"}),
    # ---- SG ----
    "sg-01-cannabis":       ("Cannabis",                              {"es":"Cannabis",   "en":"Cannabis",         "uk":"Коноплі (канабіс)","be":"Канабіс",        "zh":"大麻"}),
    "sg-02-heroina":        ("Diacetylmorphinum",                       {"es":"Heroína",    "en":"Heroin",           "uk":"Героїн",          "be":"Гераін",            "zh":"海洛因"}),
    "sg-03-metanfetamina":   ("Methamphetaminum",                       {"es":"Metanfetamina","en":"Methamphetamine","uk":"Метамфетамін","be":"Метамфетамін","zh":"甲基苯丙胺"}),
    "sg-04-mdma-(extasis)": ("Methylendioxymethamphetaminum",           {"es":"MDMA (Éxtasis)","en":"MDMA (Ecstasy)","uk":"MDMA (екстазі)","be":"MDMA (экстаз)","zh":"摇头丸(MDMA)"}),
    "sg-05-ketamina":       ("Ketaminum",                              {"es":"Ketamina",   "en":"Ketamine",         "uk":"Кетамін",         "be":"Кетамін",           "zh":"氯胺酮"}),
    "sg-06-nimetazepam-(erimin)":("Nimetazepamum",                      {"es":"Nimetazepam (Erimin)","en":"Nimetazepam (Erimin)","uk":"Німетазепам (Erimin)","be":"Німетазепам (Erimin)","zh":"甲硝西泮(Erimin)"}),
    "sg-07-pseudoefedrina": ("Pseudoephedrinum",                       {"es":"Pseudoefedrina","en":"Pseudoephedrine","uk":"Псевдоефедрин","be":"Псеўдаэфедрын", "zh":"伪麻黄碱"}),
    "sg-08-codeina":        ("Codeinum",                               {"es":"Codeína",    "en":"Codeine",          "uk":"Кодеїн",          "be":"Кодэін",            "zh":"可待因"}),
    "sg-09-chicle-de-nicotina-(alto":("Nicotinum",                     {"es":"Chicle de nicotina (alto contenido)","en":"Nicotine gum (high content)","uk":"Нікотинова жуйка (високий вміст)","be":"Нікатынавая жуйка (высакі ўтрымліванне)","zh":"尼古丁口香糖(高含量)"}),
    "sg-10-e-cigaretas-y-liquidos-c":("Nicotinum",                     {"es":"E-cigaretas y líquidos con nicotina","en":"E-cigarettes and nicotine e-liquids","uk":"Електронні сигарети та рідини з нікотином","be":"Электронныя цыгарэты і вадкасці з нікатынам","zh":"电子烟和含尼古丁烟液"}),
}

def main():
    data = json.loads(SEED.read_text(encoding="utf-8"))
    missing = []
    for code, node in data.items():
        for it in node["items"]:
            tid = it["id"]
            if tid not in T:
                missing.append(tid); continue
            latin, names = T[tid]
            it["nameLatin"] = latin
            it["names"] = names
            # keep legacy `name` (Spanish) as the fallback for old parsers
            it["name"] = names["es"]
    if missing:
        print("ERROR: ids missing from the translation table:", file=sys.stderr)
        for m in missing: print("   ", m, file=sys.stderr)
        sys.exit(1)
    # pretty-print with 2-space indent + ensure_ascii off so Cyrillic/CJK stay readable
    SEED.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"localized {sum(len(n['items']) for n in data.values())} medicines across {len(data)} countries")

if __name__ == "__main__":
    main()