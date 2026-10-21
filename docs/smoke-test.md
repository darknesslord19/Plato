# Play Otomatik Duman Testi (Smoke Test)

- **Tarih:** 2026-10-21 15:25:53
- **Uygulama Sürümü:** 1.1.2
- **Toplam Süre:** 7 dk 0 sn

## ⚠️ GERİLEMELER
Daha önce çalışan ancak bu çalıştırmada başarısız olan kontroller:

| Bölüm | Öğe / Hedef | Önceki Durum | Şimdiki Durum | Hata / Neden |
| --- | --- | --- | --- | --- |
| CloudStream | SezonlukDizi | OK | X | Extractor link çıkaramadı |

## Özet
- **CloudStream:** 19/29 oynatılabilir
- **Nuvio Addon:** 2/3 oynatılabilir
- **Nuvio JS Eklentileri:** 8/8 akış/medya başarılı
- **Altyazı Sağlayıcıları:** 3/3 Türkçe altyazı başarılı
- **Bölüm Devam:** OK (OK (S1E2, dataUrl farklı))
- **Güncelleme ve Duyuru:** 2/2 başarılı

## 1. CloudStream Eklentileri
| Eklenti | Katalog | Load | Bölüm | Link | Medya | Altyazı | Durum | Hata/Neden |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AnimeciX | OK (3 satır, 42 içerik) | OK (Chitose Is in the Ra) | OK (S1E1) | OK (1 link, 1080p) | OK (MP4 512KB) | Yok (0) | OK | - |
| Anizm | OK (10 satır, 251 içerik) | OK (Anime ara) | X (Bölüm yok) | - | - | - | X | Bölüm/dataUrl bulunamadı |
| BelgeselX | OK (10 satır, 185 içerik) | OK (Geleceğe Kalanlar) | OK (S1E1) | OK (1 link, 480p) | X (HTTP 400) | Yok (0) | X | Medya doğrulanamadı: HTTP 400 |
| DDizi | OK (4 satır, 88 içerik) | OK (Yeraltı izle) | OK (S1E19) | OK (1 link, 360p) | OK (MP4 512KB) | Yok (0) | OK | - |
| DiziBox | OK (5 satır, 121 içerik) | OK (The Drop: A Snowfall) | OK (S1E1) | OK (3 link, 2160p) | OK (M3U8 1KB) | Yok (0) | OK | - |
| DiziGom | OK (19 satır, 314 içerik) | OK (The Scandal) | OK (S1E1) | OK (2 link, 720p) | OK (M3U8 154KB) | Yok (0) | OK | - |
| DiziKorea | OK (5 satır, 75 içerik) | OK (The World of Love) | OK (Film) | OK (2 link, 360p) | OK (M3U8 1KB) | Yok (0) | OK | - |
| Dizilla | OK (12 satır, 206 içerik) | OK (VisionQuest) | OK (S1E1) | OK (4 link, 1080p) | OK (M3U8 10KB) | OK (3 sub, TÜRKÇE FORCED 34 satır) | OK | 2. denemede |
| DiziMag | X (Katalog boş ya da zaman aşımı) | - | - | - | - | - | X | Katalog boş ya da zaman aşımı |
| DiziMom | OK (5 satır, 121 içerik) | OK (The Scandal) | OK (S1E1) | OK (1 link, 360p) | OK (M3U8 1KB) | Yok (0) | OK | - |
| DiziPal | X (Katalog boş ya da zaman aşımı) | - | - | - | - | - | X | Katalog boş ya da zaman aşımı |
| FilmMakinesi | OK (10 satır, 172 içerik) | OK (Moana izle (2026)) | OK (Film) | OK (2 link, 1080p) | OK (M3U8 2KB) | X (zaman aşımı) | OK | - |
| FilmModu | OK (15 satır, 278 içerik) | OK (Beetlejuice Beetleju) | OK (Film) | OK (8 link, 1080p) | OK (M3U8 213KB) | OK (1 sub, TÜRKÇE 1470 satır) | OK | - |
| FullHDFilm | OK (3 satır, 75 içerik) | OK (Uykucu (2025)) | OK (Film) | OK (1 link, 360p) | X (Connection closed by peer) | X (zaman aşımı) | X | Medya doğrulanamadı: Connection closed by peer |
| HDFilmCehennemi | OK (6 satır, 129 içerik) | OK (Moana 2026) | OK (Film) | OK (2 link, 360p) | OK (M3U8 2KB) | OK (2 sub, İNGILIZCE ALTYAZI 1244 satır) | OK | - |
| HDFilmIzle | OK (16 satır, 205 içerik) | OK (Of-Pof Balık (2026) ) | OK (Film) | OK (1 link, 360p) | X (Connection closed by peer) | Yok (0) | X | Medya doğrulanamadı: Connection closed by peer |
| HintFilmIzle | OK (10 satır, 72 içerik) | OK (Jailer 2) | OK (Film) | OK (3 link, 480p) | OK (M3U8 117KB) | Yok (0) | OK | - |
| JetFilmizle | OK (22 satır, 217 içerik) | OK (Düşler) | OK (Film) | OK (6 link, 360p) | OK (M3U8 1KB) | Yok (0) | OK | - |
| KultFilmler | OK (24 satır, 311 içerik) | OK (Être et avoir – Olma) | OK (Film) | OK (2 link, 360p) | OK (M3U8 1KB) | OK (1 sub, TÜRKÇE 1256 satır) | OK | - |
| SelcukFlix | OK (10 satır, 95 içerik) | OK (Spider-Man: Brand Ne) | OK (Film) | OK (2 link, 360p) | OK (M3U8 10KB) | OK (2 sub, İNGILIZCE 1945 satır) | OK | - |
| SetFilmIzle | OK (27 satır, 365 içerik) | OK (Yolda Kaldım) | OK (Film) | OK (1 link, 1080p) | OK (M3U8 83KB) | OK (4 sub, TR 641 satır) | OK | - |
| SezonlukDizi | OK (9 satır, 176 içerik) | OK (Brothers) | OK (S1E1) | X (0 link / zaman aşımı) | - | - | X | Extractor link çıkaramadı |
| SinemaCX | OK (21 satır, 200 içerik) | OK (Hazine Avcısının Mac) | OK (Film) | OK (3 link, 360p) | OK (M3U8 2KB) | OK (1 sub, TURKISH 772 satır) | OK | - |
| SinemaTV | OK (8 satır, 254 içerik) | OK (Дракон-горничная гос) | OK (Film) | OK (4 link, 720p) | OK (M3U8 86KB) | OK (6 sub, ENGLISH 1479 satır) | OK | - |
| Sinewix | X (Katalog boş ya da zaman aşımı) | - | - | - | - | - | X | Katalog boş ya da zaman aşımı |
| TavsiyeFilmIzle | OK (20 satır, 139 içerik) | OK (Nasıl Katil Olunur H) | OK (Film) | X (0 link / zaman aşımı) | - | - | X | Extractor link çıkaramadı |
| Watch2Movies | X (Katalog boş ya da zaman aşımı) | - | - | - | - | - | X | Katalog boş ya da zaman aşımı |
| WebteIzle | OK (8 satır, 117 içerik) | OK (Nutcrackers) | OK (Film) | OK (2 link, 360p) | OK (M3U8 1KB) | Yok (0) | OK | - |
| YabanciDizi | OK (16 satır, 235 içerik) | OK (3Below: Tales of Arc) | OK (S1E1) | OK (12 link, 360p) | OK (M3U8 1KB) | Yok (0) | OK | 2. denemede |

## 2. Nuvio Addon'ları
| Addon | Tür | Akış | Medya | Altyazı | Durum | Hata/Neden |
| --- | --- | --- | --- | --- | --- | --- |
| İzlemeli.com Katalog | Video Addon | X (0 akış / zaman aşımı) | - | Yok (0) | X | Akış bulunamadı / zaman aşımı |
| Torrentio | Video Addon | OK (96 akış) | torrent, atlandı | Yok (0) | OK | - |
| Nexus | Video Addon | OK (5 akış) | torrent, atlandı | Yok (0) | OK | - |

## 3. Nuvio JS Eklentileri (Scrapers)
| Eklenti | Hedef | Akış | Medya | Altyazı | Durum | Sınıf | Hata/Neden |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AnimeCix | Anime (tt0388629 S1B1) | OK (3 akış) | OK (MP4 512KB) | Yok (0) | OK | - | - |
| Anizium | Anime (tt0388629 S1B1) | OK (5 akış) | OK (MP4 512KB) | OK (35 sub, TR 355 satır) | OK | - | - |
| Dizilla | Dizi (tt0903747 S1B1) | OK (1 akış) | OK (M3U8 10KB) | OK (2 sub, TR 593 satır) | OK | - | - |
| FullHDFilmizlesene | Film (tt0137523) | OK (1 akış) | OK (M3U8 3KB) | OK (1 sub, TR 1733 satır) | OK | - | - |
| HDFilmCehennemi | Film (tt0137523) | OK (1 akış) | OK (M3U8 1KB) | OK (6 sub, TR 1811 satır) | OK | - | - |
| HDFilmCehennemi | Dizi (tt0903747 S1B1) | OK (1 akış) | OK (M3U8 1KB) | OK (5 sub, TR 593 satır) | OK | - | - |
| SelcukFlix | Film (tt0137523) | OK (1 akış) | OK (M3U8 10KB) | OK (3 sub, TR 1797 satır) | OK | - | - |
| SelcukFlix | Dizi (tt0903747 S1B1) | OK (1 akış) | OK (M3U8 10KB) | OK (2 sub, TR 593 satır) | OK | - | - |

## 4. Altyazı Sağlayıcıları
| Sağlayıcı | Hedef | Türkçe Arama | İndirme & Ayrıştırma | Durum | Hata/Neden |
| --- | --- | --- | --- | --- | --- |
| AltyazıDB | tt0137523 | OK (TR 10, toplam 10) | OK (1797 satır) | OK | - |
| AltyazıTR | tt0137523 | OK (TR 10, toplam 10) | OK (1732 satır) | OK | - |
| Yerleşik Çevrimiçi Arama | tt0137523 | OK (TR 0, toplam 7) | OK (1864 satır) | OK | - |

## 5. Devam Et (CsNextEpisodeFinder)
| Bileşen | Hedef | Sonuç | Durum | Hata/Neden |
| --- | --- | --- | --- | --- |
| CsNextEpisodeFinder | AnimeciX (Chitose Is in the Ra) S1B1 | OK (S1E2, dataUrl farklı) | OK | - |

## 6. Güncelleme ve Duyuru Kontrolü
| Kaynak | Detay | Durum | Hata/Neden |
| --- | --- | --- | --- |
| GitHub releases/latest | OK (tag: v1.1.2) | OK | - |
| announcements.json | OK (1 duyuru) | OK | - |

## 7. Oynatıcı (sarma)
| Kontrol | Hedef | Sonuç | Durum | Hata/Neden |
| --- | --- | --- | --- | --- |
| Kumandayla sarma | 3 x Sağ, 1 x Sol (çubuk odakta) | 1:13 → 1:23 → 1:33 → 1:43 → 1:33 | OK | - |
| Sürükleyerek sarma | çubukta %10 → %50 | 5:11 / %49 | OK | - |
