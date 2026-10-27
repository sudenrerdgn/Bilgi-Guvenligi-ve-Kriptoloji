# Proje kararları ve ilerleme

## Kabul edilen kararlar

- Java 25+; Windows; VS Code; en az iki fiziksel bilgisayar.
- Bir Java sunucusu ve iki Java istemcisi, her uygulama için yerel web arayüzü.
- İstemciler doğrudan bağlanmaz; eşzamanlı mesajlaşma desteklenir.
- Sunucu ekranı ayrı tarayıcı penceresinde açılır.
- Türkçe arayüz; Türkçe ancak Türkçe karakter içermeyen kod yorumları.
- İngilizce sınıf, değişken ve fonksiyon isimleri.
- Orta seviyede, sunumda açıklanabilir bir yapı; geliştirme adım adım yapılır.
- PDF sunucuda otomatik kaydedilir, istemcide İndir düğmesiyle alınır.

## Görev dağılımı

| Üye | Sorumluluk |
|---|---|
| Sude | Sunucu, bağlantı yönetimi, mesaj/PDF yönlendirme, sunucu ekranı, sistem mimarisi |
| Ece | Anahtar paylaşımı, AES şifreleme/çözme, güvenli mod açıklaması |
| Zeynep | İstemci arayüzü, dosya seçimi ve ilerleme, ölçümlerin gösterimi |

Manuel AES ve Wireshark deneyleri ortak çalışmadır. Her üye kendi bölümünü
çalıştırıp açıklayabilmeli ve raporun ilgili bölümünü hazırlamalıdır.
Grup formu henüz teslim edilmedi; ad-soyad, öğrenci numarası ve GitHub bağlantısı
eklenerek görev dağılımıyla birlikte hazırlanmalıdır. Kişisel bilgiler burada tutulmaz.

## Dört haftalık sıra

1. Proje kurulumu, ekranlar ve güvensiz metin iletişimi.
2. Parçalı PDF aktarımı, ilerleme/indirme, AES-128 ve ilk anahtar paylaşımı.
3. Dört yöntemin tamamlanması, manuel AES ve kütüphane karşılaştırması.
4. İki bilgisayarda deneyler, ölçümler, Wireshark kayıtları, 6–10 sayfa rapor ve sunum.

Kesin teslim günü kullanıcıdan henüz alınmadı; süre yaklaşık bir ay.

## İlerleme

- [x] Java uygulama iskeleti ve çalıştırma betikleri
- [x] Türkçe istemci ve sunucu ekranları
- [x] Sunucu üzerinden güvensiz metin iletimi
- [x] İki istemci sınırı, bağlantı durumu ve bellekte mesaj geçmişi
- [ ] Kullanıcının iki bilgisayarda ilk denemesi
- [x] PDF aktarımı, sunucuda kayıt, istemcide indirme ve ilerleme
- [x] Alıcıda kayıt onayı ve PDF aktarımının otomatik testleri
- [x] Sıkıştırılmamış örnek PDF ve Wireshark kayıt rehberi
- [x] Sunucu ekranında güvenli/güvensiz mod geçişi ve eski oturumların kapatılması
- [x] AES-128-GCM ile güvenli metin aktarımı
- [x] RSA-2048, DH-2048, ECDH P-256 ve ML-KEM-768
- [ ] Güvenli PDF aktarımı (kullanıcının seçimiyle sonraki aşama)
- [ ] Manuel AES ve aynı girdide kütüphane karşılaştırması
- [x] Her yöntem için 10 gerçek bağlantı ölçümü, ortalamalar, boyut tablosu ve CSV altyapısı
- [ ] İki fiziksel bilgisayarda karşılaştırma deneyi ve rapora alınacak sonuçlar
- [ ] Sunucu ve istemci tarafında Wireshark kayıtları
- [ ] Rapor, README'nin son hali ve sunum

## Güvenli metin aşamasında seçilen parametreler

- Java JCA/JCE AES-128-GCM; 96 bit nonce, 128 bit doğrulama etiketi.
- RSA-2048 OAEP SHA-256 / MGF1-SHA256; DH RFC 3526 grup 14; ECDH secp256r1;
  Java 25 yerleşik ML-KEM-768.
- HKDF-SHA256 ile yönlere ayrı AES-128 anahtarları ve nonce ön ekleri.
- Her sunucu başlangıcında Ed25519 kimliği; istemcide sunucu SHA-256 parmak izi kontrolü.
- Güvenli modda PDF gönderimi hem ekranda hem Java tarafında kapalı.
- Ölçüm sınırları ve çalışma adımları `docs/SECURE_MODE.md` içinde.
- Manuel AES henüz uygulanmadı. Ağda şu aşamada Java AES kütüphanesi kullanılır;
  manuel AES ve aynı girdide kütüphane karşılaştırması ayrı sonraki çalışmadır.
Güvenli mod istemci–sunucu bağlantıları içindir: sunucu içeriği çözer ve hedef
istemcinin anahtarıyla yeniden şifreler. Uçtan uca şifreleme olarak sunulmaz.

## Hocanın geri bildirimi

Güvensiz moddaki metin/PDF aktarımı tamamlanınca, güvenli moda başlamadan önce
iki bilgisayarda ilk Wireshark kaydı alınacak. `.pcapng` dosyaları
`captures/insecure/` klasörüne eklenecek. Yönergeler `docs/WIRESHARK.md` içinde.
Repoya sunucu ve istemci için iki güvensiz `.pcapng` dosyası eklenmiş durumda.
Kayıtların içeriği bu geliştirmede doğrulanmadı; deney bilgileri tablosu henüz boş.
Güvenli modun iki fiziksel bilgisayarda deneyi ve gerçek kayıtları bekliyor.
