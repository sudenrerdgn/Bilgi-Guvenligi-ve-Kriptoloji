# Güvenli metin ve PDF aktarımı — Zeynep ve Ece için çalışma rehberi

Bu aşamada sunucu mod düğmesi, dört anahtar paylaşım yöntemi, şifreli metin/PDF ve
karşılaştırma ölçümleri vardır. PDF her iki modda çalışır. Ağda Java JCA/JCE
kullanılır. Ayrı [manuel AES-128 çekirdeği](MANUAL_AES.md) ve blok doğruluk testleri
de bulunur.

## 1. Başlatın

Her bilgisayarda `java -version` ve `javac -version` ile **JDK 25 veya üzeri**
olduğunu kontrol edin. Java 21 bu sürümü çalıştırmaz. Harici bağımlılık yoktur.
Çalışan eski uygulamaları Ctrl+C ile kapatın. Proje klasöründe:

```powershell
.\scripts\build.ps1
```

Üç ayrı terminalde:

```powershell
.\scripts\run.ps1 -Role server -WebPort 8080 -TcpPort 5000
```

```powershell
.\scripts\run.ps1 -Role client -WebPort 8081
```

```powershell
.\scripts\run.ps1 -Role client -WebPort 8082
```

Sunucu `http://127.0.0.1:8080`, istemciler `http://127.0.0.1:8081` ve
`http://127.0.0.1:8082` adresinde açılır. Betik engellenirse örnek kullanım:
`powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1`.

## 2. Sunucuda modu değiştirin

1. Sunucu ekranındaki **Güvenli moda geç** düğmesine basın.
2. Önceden bağlanan istemcilerin ayrıldığını görün. Devam eden aktarımlar ve anahtar
   paylaşımları da kapatılır. Mod değişiminden sonra yeniden bağlantı gerekir.
3. **Parmak izini kopyala** düğmesini kullanın. Bu değer gizli anahtar değildir;
   sunucunun kimlik açık anahtarının SHA-256 özetidir.
4. Sunucu her yeniden başlatıldığında yeni kimlik üretir ve güvensiz modda açılır.
   Bu nedenle her başlangıçta modu seçip yeni parmak izini paylaşın.

## 3. İki istemciyi bağlayın

1. Kullanıcı adlarını `Zeynep` ve `Ece` yapın.
2. Tek bilgisayarda IP `127.0.0.1`, port `5000` kullanın. İki bilgisayarda
   istemcilerde **sunucu bilgisayarının LAN IPv4 adresini** girin.
3. İki istemcide **Güvenli** modu seçin. İlk deneyde ikisinde de **RSA-2048** seçin.
4. Sunucuyu çalıştıran arkadaşınızın ekranından/ayrı güvenilir iletişimden aldığınız
   parmak izini girin. Bilinmeyen sunucunun ağdan sunduğu değeri otomatik kabul etmeyin.
5. **Sunucuya bağlan** düğmesine basın. Kimlik, anahtar ve şifreli onay doğrulanınca
   ekranda **Güvenli mod · AES-128-GCM** ve **Bağlı** görünür.
6. İki yönde metin gönderin. Sunucuda okunabilir içerik görünmesi beklenir:
   sunucu çözer ve alıcı bağlantısının ayrı anahtarıyla yeniden şifreler.
7. **PDF gönder** bölümünden `samples/wireshark-test.pdf` seçin. **Alıcıya teslim
   edildi** yazısını bekleyin, diğer istemcide **İndir** ile açın. Ters yönde de
   gönderin. Güvenli modda PDF adı, parçaları ve teslim onayı şifrelenir.
8. Bağlantıları kesip diğer yöntemleri deneyin. İstemciler farklı yöntem de
   seçebilir; her istemci–sunucu bağlantısı bağımsızdır.

Sunucu ile istemci farklı moddaysa bağlantı reddedilir. Güvenli bağlantı isteği
başarısız olduğunda otomatik olarak güvensiz moda düşülmez. Yanlış parmak izi,
geçersiz imza, değiştirilmiş/tekrarlanmış şifreli kayıt bağlantının reddine yol açar.
PDF sınırı 10 MiB (10 × 1024 × 1024 bayt), parça boyutu 32 KiB'dır.
İlerleme %100 olduğunda sunucuya gönderim bitmiştir; başarı için alıcının kayıt
onayı da gerekir. Sunucu ve alıcı dosyayı okunabilir halde diske kaydeder;
AES ağ üzerindeki aktarımı korur.

## 4. Süre ve boyutları karşılaştırın

Sunucuyu güvenli modda bırakın. Bir istemcide bağlantıyı kesin; sunucu adresini,
parmak izini ve yöntemi seçip **Seçili yöntemle 10 ölçüm yap** düğmesine basın.
Bu işlem gerçek istemci–sunucu TCP bağlantılarıyla 10 anahtar paylaşımı yapar.
Ölçüm bağlantıları sohbetin iki istemci kontenjanına eklenmez ve mesaj göndermez.
İlerlemenin 10/10 olmasını bekleyip dört yöntem için tekrarlayın.

Tablo her yöntem için son **tamamlanan 10 tekrarın ortalamasını** gösterir.
Başarısız/yarım deney tabloya eklenmez; önceki tamamlanmış deney korunur. CSV
indirmesinde her tekrarın gerçek değerleri, sunucu adresi ve deney zamanı bulunur.
Sonuçlar bellektedir; istemciyi kapatmadan CSV'yi indirin. Farklı adreslerdeki veya
farklı koşullardaki sonuçları aynı deneymiş gibi yorumlamayın.

| Sütun | Ölçüm sınırı |
|---|---|
| Sunucu anahtar üretimi | Seçili yöntemin yeni anahtar çiftinin üretimi ve parametre kurulumu; `System.nanoTime()` |
| İstemci anahtar/ortak sır üretimi | RSA: rastgele 16 bayt anahtar; DH/ECDH: anahtar çifti; ML-KEM: ortak sır ve kapsülleme çıktısı üretimi |
| Paylaşım süresi | İstemcinin `SECURE_HELLO`/`MEASURE_HELLO` yazmaya başlamasından şifreli `FINISHED_ACK` doğrulamasına kadar geçen duvar süresi |
| Anahtar yükü | Sunucu yöntem açık anahtarının X.509 kodlaması + istemci yöntem yanıtı (açık anahtar, RSA şifreli anahtar veya ML-KEM kapsülü) |
| Toplam paylaşım trafiği | HELLO, KEY_SERVER, KEY_CLIENT, FINISHED ve FINISHED_ACK çerçevelerinin iki yöndeki toplam baytı; uzunluk alanları, kimlik anahtarı, imza ve GCM ekleri dahil |

Paylaşım süresi anahtar üretimini, kimlik doğrulamasını, HKDF'yi, ağ beklemesini
ve şifreli onayı içerir; saf algoritma çalışma süresi değildir. TCP bağlantı kurma,
sonraki READY mesajı, sohbet mesajları, HTTP ve 800 ms ekran sorgulaması dışarıdadır.
Sunucu kimlik anahtarı bir kez üretildiğinden yöntem üretim sürelerine dahil değildir.
ML-KEM kapsülleme, diğer yöntemlerin anahtar çifti üretimiyle aynı işlem değildir;
raporda bu farkı belirtin. JVM ısınması, RSA üretim değişkenliği ve ağ yükü etkiler.
İlk turu ısınma olarak çalıştırıp ikinci tam turu rapora almak tercih edilebilir;
hangi turu aldığınızı deney notunda belirtin.

**Uygulama baytı, Ethernet/TCP paket boyutu değildir.** TCP bir çerçeveyi bölebilir
veya birden fazla çerçeveyi birleştirebilir. TCP/IP başlıkları, ACK paketleri ve
yeniden iletimler bu tabloya dahil değildir. Ödevdeki ağ paket boyutu karşılaştırmasını
Wireshark kaydıyla ayrıca doğrulayın; raporda kullandığınız ölçüm katmanını yazın.

## 5. Wireshark ile gerçek deneyi kaydedin

İki fiziksel bilgisayarda sunucu ve istemci tarafında kayıt alın. Arayüz seçimi ve
filtre ayrıntıları [mevcut Wireshark rehberinde](WIRESHARK.md) bulunur.

1. İki tarafta **yakalama filtresi** `tcp port 5000` ile kaydı başlatın.
2. Sunucuyu güvenli moda alın, yöntemi seçin ve iki istemciyi bağlayın.
3. `GUVENLI-METIN-TEST-001` ve ters yönde `GUVENLI-YANIT-002` gönderin.
4. Örnek PDF'yi iki yönde gönderin ve her aktarımda teslim onayını bekleyin.
   Kaynak ve indirilen PDF için `Get-FileHash -Algorithm SHA256 -LiteralPath DOSYA_YOLU`
   sonuçlarını karşılaştırın.
5. **Görüntüleme filtresi** `tcp.port == 5000`, ardından **Follow → TCP Stream**
   ile akışı inceleyin. Mesaj metinleri okunmamalı; `SECURE` zarfı, yöntem,
   kullanıcı adı ve açık anahtar paylaşım bilgileri görülebilir. PDF adı,
   `%PDF-1.4`, `KRIPTOLOJI GUVENSIZ PDF TESTI` içeriği ve `FILE_ACK` açık görünmemeli.
   Paket boyutları ve zamanlaması gizlenmez.
6. Anahtar paylaşımını yakalamak için kaydı bağlantıdan önce başlatın. Karşılaştırma
   düğmesi her tekrar için yeni TCP bağlantısı açar; akışları ayrı inceleyin.
7. Ana pencereden **File → Save As** ile gerçek `.pcapng` kayıtlarını kaydedin.
   Önerilen klasör `captures/secure/`; örneğin `rsa-sunucu-metin-pdf.pcapng` ve
   `rsa-istemci-metin-pdf.pcapng`. Diğer yöntemlerde aynı senaryoyu uygulayın.
8. Tarih, iki bilgisayarın IP/JDK/Wireshark sürümleri, yöntem, kaynak commit,
   stream numaraları ve CSV dosyasını birlikte kaydedin.

Otomatik testin TCP bayt kontrolü gerçek iki bilgisayarlı Wireshark deneyinin
yerine geçmez. Bu geliştirme sırasında güvenli `.pcapng` kaydı üretilmedi.

## 6. Kodda akış

```text
Sunucu: Güvenli moda geç → eski soketleri kapat
İstemci → SECURE_HELLO (kullanıcı, yöntem, rastgele değer)
Sunucu → KEY_SERVER (yöntem açık anahtarı, kimlik açık anahtarı, imzalı yanıt)
İstemci: sunucu parmak izini ve yanıt imzasını doğrula
İstemci → KEY_CLIENT (RSA ile şifreli anahtar / DH-ECDH açık değeri / ML-KEM kapsülü)
İki taraf: ortak sır + konuşma özeti → HKDF → yönlere ayrı AES-128 anahtarları
İstemci → AES-GCM(FINISHED)
Sunucu → AES-GCM(FINISHED_ACK) → AES-GCM(READY)
İstemci A → AES-GCM(CHAT) → Sunucu çözer
Sunucu → alıcı B'nin anahtarıyla AES-GCM(MESSAGE)
Sunucu → A'nın anahtarıyla AES-GCM(SENT)
İstemci A → AES-GCM(FILE_START / FILE_CHUNK / FILE_END) → Sunucu çözer ve kaydeder
Sunucu → B'nin anahtarıyla AES-GCM(FILE_START / FILE_CHUNK / FILE_END)
İstemci B → kaydet → AES-GCM(FILE_ACK) → Sunucu → A'nın anahtarıyla AES-GCM(FILE_ACK)
```

`SENT`, alıcının soketine yazma onayıdır; alıcının okuduğu anlamına gelmez.
Her bağlantının yönlerine ayrı 128 bit anahtar ve 4 bayt nonce ön eki türetilir.
96 bit GCM nonce, bu ön ek ve 64 bit monoton sayaçtan oluşur. Etiket 128 bittir.
Sayaç ve protokol etiketi AAD ile doğrulanır; sıra hataları ve tekrarlar reddedilir.
Oturum anahtarları/parolalar ekrana veya CSV'ye yazılmaz.

| Yöntem | Parametre |
|---|---|
| RSA | 2048 bit, OAEP SHA-256, MGF1 SHA-256; istemci 16 bayt rastgele başlangıç anahtarı üretir |
| DH | RFC 3526 grup 14, 2048 bit asal, g=2; açık değer ve alt grup doğrulaması |
| ECDH | secp256r1 / NIST P-256; Java anahtar doğrulaması |
| ML-KEM | ML-KEM-768, Java'nın yerleşik KEM API'si |
| Ortak sırdan türetme | HKDF-SHA256; tuz olarak el sıkışma özeti; yönlere ayrı AES anahtarları |
| Sunucu kimliği | Ed25519 imza + elle alınan SHA-256 parmak izi |

ML-KEM anahtar paylaşımı kuantum sonrası yöntemdir; bu uygulamanın Ed25519 kimlik
imzası klasik kriptografidir. Bu yüzden tüm sistem kuantuma dayanıklı diye sunulmaz.
İstemci kullanıcı adları kimlik doğrulaması değildir. Sunucu içerikleri açık halde
işler; bu sistem uçtan uca şifreleme yapmaz. Ders için geliştirilen bu protokol,
üretim sistemlerindeki standart TLS'nin yerine konulmamalıdır.

## 7. Test ve görevler

```powershell
.\scripts\test.ps1
```

Mevcut metin/PDF testlerine ek olarak dört yöntem, gerçek TCP'de şifreli metin,
mod geçişi, yanlış kimlik, bozuk GCM etiketi, tekrar/sıra kontrolü, onarlı ölçümler
ve güvenli PDF aktarımı kontrol edilir. HTTP yükleme/indirme, 10 MiB sınırı,
eşzamanlı iki yönlü PDF, kesinti temizliği ve bozuk PDF GCM etiketinin reddi test edilir.
Gerçek TCP'yi aktaran test vekili, el sıkışmasından sonra PDF ve onaylar dahil
yalnızca `SECURE` çerçeveleri taşındığını kontrol eder.
HKDF bağımsız RFC test vektörüyle doğrulanır.

- **Zeynep:** `index.html`, `app.js`, `style.css`; mod ve yöntem seçimi,
  bağlantı durumu, ölçüm tablosu ve CSV kullanımını açıklama.
- **Ece:** `Crypto.java`, `KeyExchange.java`, `SecureChannel.java`; her yöntemin
  ortak sırrı nasıl oluşturduğunu, AES-GCM ve nonce düzenini açıklama.
- **Sude:** `RelayServer.java`, `WebUi.java`; mod geçişi, oturumlar ve yeniden
  şifreleme akışını açıklama.

Ece'nin bu haftaki sunumu için [AES-128 ve RSA-2048 açıklaması](ECE_AES_RSA.md).

Sonraki aşamalar: güvenli modda iki bilgisayarlı deney, Wireshark ve rapor.
Manuel çekirdekle Java AES arasında performans karşılaştırması henüz yapılmadı.

## Kaynaklar

- [Java 25 Cipher / AES-GCM](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Cipher.html)
- [Java 25 KEM API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KEM.html)
- [OAEP parametreleri](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/spec/OAEPParameterSpec.html)
- [RFC 3526 — DH grup 14](https://www.rfc-editor.org/rfc/rfc3526.html#section-3)
- [RFC 5869 — HKDF](https://www.rfc-editor.org/rfc/rfc5869.html)
