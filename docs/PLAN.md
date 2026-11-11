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
- [x] Kullanıcının iki bilgisayarda ilk denemesi; iki kayıt içeriği doğrulandı
- [x] PDF aktarımı, sunucuda kayıt, istemcide indirme ve ilerleme
- [x] Alıcıda kayıt onayı ve PDF aktarımının otomatik testleri
- [x] Sıkıştırılmamış örnek PDF ve Wireshark kayıt rehberi
- [x] Sunucu ekranında güvenli/güvensiz mod geçişi ve eski oturumların kapatılması
- [x] AES-128-GCM ile güvenli metin aktarımı
- [x] RSA-2048, DH-2048, ECDH P-256 ve ML-KEM-768
- [x] Güvenli PDF aktarımı; şifreli parçalar ve alıcı kayıt onayı, 10 MiB sınırı
- [x] Manuel AES-128: anahtar genişletme, şifreleme/çözme turları, ara adım demosu
- [x] Manuel AES ile Java AES'in aynı blokta doğruluk karşılaştırması
- [x] Her yöntem için 10 gerçek bağlantı ölçümü, ortalamalar, boyut tablosu ve CSV altyapısı
- [x] İki fiziksel bilgisayarda karşılaştırma deneyi ve rapor sonuç tablosu
  (`captures/key-exchange/` altında iki koşunun CSV'leri; donanım/JDK/ağ bilgileri ayrıca kaydedilmeli)
- [x] Güvensiz modda sunucu ve istemci Wireshark kayıtları; metin/PDF/ACK doğrulaması
- [ ] Güvenli modda sunucu ve istemci Wireshark kayıtları
- [x] Raporun Word taslağı: giriş, mimari, güvenli akış, dört yöntem, manuel AES ve mevcut ölçümler (`docs/RAPOR.docx`)
- [ ] Raporun güvenli Wireshark analizi, kapak bilgileri ve son düzenlemesi; README'nin son hali ve sunum

## Güvenli aktarım aşamasında seçilen parametreler

- Java JCA/JCE AES-128-GCM; 96 bit nonce, 128 bit doğrulama etiketi.
- RSA-2048 OAEP SHA-256 / MGF1-SHA256; DH RFC 3526 grup 14; ECDH secp256r1;
  Java 25 yerleşik ML-KEM-768.
- HKDF-SHA256 ile yönlere ayrı AES-128 anahtarları ve nonce ön ekleri.
- Her sunucu başlangıcında Ed25519 kimliği; istemcide sunucu SHA-256 parmak izi kontrolü.
- Güvenli modda metin ve tüm PDF çerçeveleri aynı AES kanalını kullanır.
- Ece'nin bu haftaki tercihi: RSA-2048 ile ilk deney, AES-128-GCM ve Java
  kütüphanesi; mesaj/PDF, mod seçimi, testler ve açıklama dokümanı birlikte.
- Ölçüm sınırları ve çalışma adımları `docs/SECURE_MODE.md` içinde.
- Manuel AES-128 blok çekirdeği kriptografi API'si kullanmadan eklendi.
  Anahtar genişletme ve tur işlemleri docs/MANUAL_AES.md içinde açıklanır.
  Bilinen vektörler ve 1000 girdide Java AES ile doğruluk karşılaştırması yapılır.
  Ağda Java AES-GCM kullanılır; manuel çekirdeğin GCM entegrasyonu ve performans
  karşılaştırması bu çalışmanın kapsamına dahil değildir.
Güvenli mod istemci–sunucu bağlantıları içindir: sunucu içeriği çözer ve hedef
istemcinin anahtarıyla yeniden şifreler. Uçtan uca şifreleme olarak sunulmaz.

## Hocanın geri bildirimi

Güvensiz moddaki metin/PDF aktarımı tamamlanınca, güvenli moda başlamadan önce
iki bilgisayarda ilk Wireshark kaydı alınacak. `.pcapng` dosyaları
`captures/insecure/` klasörüne eklenecek. Yönergeler `docs/WIRESHARK.md` içinde.
Repoya sunucu ve istemci için iki güvensiz `.pcapng` dosyası eklenmiş durumda.
Kayıtların TCP akışları yeniden birleştirildi: iki yönlü metin, iki PDF aktarımı ve
alıcı onayları doğrulandı. Her iki kayıttaki PDF baytları kaynak örnekle aynı.
Paket numaraları, sürümler, IP'ler ve SHA-256 sonucu `captures/insecure/README.md` içinde.
Güvenli modun iki fiziksel bilgisayarda deneyi ve gerçek kayıtları bekliyor.
