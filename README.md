# Bilgi Güvenliği ve Kriptoloji

Java sunucusu ve iki Java istemcisinden oluşan, Türkçe web arayüzlü ders projesi.
Her uygulama kendi bilgisayarında çalışır; arayüzü o bilgisayarın tarayıcısında açılır.

**Mevcut aşama:** Sunucuda güvenli/güvensiz mod geçişi; AES-128-GCM ile güvenli
metin aktarımı; RSA-2048, DH-2048, ECDH P-256 ve ML-KEM-768 anahtar paylaşımı.
İstemci ekranında her yöntem için 10 gerçek bağlantı ölçümü, ortalamalar ve CSV
indirme bulunur. PDF aktarımı şimdilik yalnızca güvensiz modda çalışır.

**Başlangıç:** [Güvenli modu birlikte çalıştırma rehberi](docs/SECURE_MODE.md).
Güvensiz `.pcapng` dosyaları `captures/insecure/` altında bulunuyor; içeriklerinin
doğrulanması ve deney bilgilerinin doldurulması gerekiyor. Güvenli modun iki
bilgisayarda Wireshark deneyi henüz yapılmadı.

## Görev Dağılımı:
| Üye | Ana sorumluluk | Raporda anlatacağı bölüm |
|---|---|---|
| Sudenur Erdoğan | Java sunucusu, bağlantıları yönetme, mesaj ve PDF yönlendirme, sunucu ekranı | Sistem mimarisi ve sunucunun çalışma akışı |
| Ece Naz Genç| RSA, DH, ECDH, ML-KEM ile anahtar paylaşımı; AES ile şifreleme/çözme | Güvenli mod ve anahtar dağıtım yöntemleri |
| Zeynep Yılmaz| İstemci web arayüzü, mesaj geçmişi, dosya seçimi, aktarım durumu ve ölçüm sonuçlarının gösterimi | İstemci kullanımı ve karşılaştırma sonuçları |

## Gereksinimler

- Windows, VS Code ve **JDK 25 veya üzeri** (`java` ve `javac` aynı güncel JDK'ya ait olmalı).
- ML-KEM dahil Java'nın yerleşik kriptografi API'leri kullanılır; Maven, Gradle veya harici Java kütüphanesi gerekmez.
- İki bilgisayarla deney için aynı yerel ağ ve sunucunun TCP 5000 portuna erişim.

## Derleme ve test

Proje klasöründe PowerShell terminali açın:

```powershell
.\scripts\build.ps1
.\scripts\test.ps1
```

PowerShell betik çalıştırmayı engellerse yalnızca ilgili komut için şu biçimi kullanabilirsiniz:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1
```

Bu kullanım kalıcı yürütme politikasını değiştirmez. Aynı biçim `run.ps1` ve
`test.ps1` için de kullanılabilir. Kod değişince uygulamaları durdurun, yeniden
derleyin ve başlatın. Çalışan uygulama `Ctrl+C` ile durdurulur.

## Tek bilgisayarda ilk deneme

Sunucu her başlatıldığında **güvensiz modda** açılır. Aşağıdaki ilk denemeden sonra
sunucu ekranındaki **Güvenli moda geç** düğmesini kullanın. Mevcut istemciler ayrılır;
iki istemcide de Güvenli mod seçip yöntem ve sunucu parmak izi ile yeniden bağlanın.
Parmak izi her sunucu başlangıcında yenilenir. Ayrıntılar: [Güvenli mod](docs/SECURE_MODE.md).

Derledikten sonra VS Code içinde **üç ayrı terminalde** şu komutları çalıştırın:

```powershell
# Terminal 1: Sunucu
.\scripts\run.ps1 -Role server -WebPort 8080 -TcpPort 5000
```

```powershell
# Terminal 2: Birinci istemci
.\scripts\run.ps1 -Role client -WebPort 8081
```

```powershell
# Terminal 3: Ikinci istemci
.\scripts\run.ps1 -Role client -WebPort 8082
```

Tarayıcıda şu adresleri açın:

| Ekran | Adres |
|---|---|
| Sunucu | http://127.0.0.1:8080 |
| İstemci A | http://127.0.0.1:8081 |
| İstemci B | http://127.0.0.1:8082 |

İstemcilerde farklı kullanıcı adları girin. Sunucu adresi `127.0.0.1`, haberleşme
portu `5000` olsun. İkisini de bağlayıp karşılıklı mesaj gönderin. Sunucu ekranında
iki kullanıcıyı ve iletilen mesajları göreceksiniz. Üçüncü bağlantı ve tekrar eden
kullanıcı adı reddedilir. İki taraf aynı anda mesaj gönderebilir.

## İki bilgisayarda çalışma

1. Her iki bilgisayarda projeyi derleyin.
2. Bilgisayar A'da yalnızca sunucuyu çalıştırın; sunucu ekranını A'da açın.
3. A'da `ipconfig` ile aynı Wi-Fi/Ethernet ağına ait IPv4 adresini bulun.
4. Bilgisayar B'de istemcileri 8081 ve 8082 web portlarıyla başlatın; iki ekranı B'de açın.
5. İstemcilerde sunucu IP'si olarak A'nın IPv4 adresini, TCP portu olarak `5000` girin.
6. Windows Güvenlik Duvarı erişimi engelliyorsa Java veya TCP 5000 için kullanılan
   özel ağ profilinde gelen bağlantıya izin verin. Ağı ve portu doğrulayın.

İsterseniz istemcilerden birini A'da, diğerini B'de çalıştırabilirsiniz.
Web ekranları yalnızca `127.0.0.1` üzerinde dinler; başka bilgisayardan açılmaz.
Bilgisayarlar arasındaki bağlantı Java istemcisi ile Java sunucusu arasında kurulur.
Tek bilgisayar testi, ödevdeki en az iki bilgisayar şartını karşılamaz.

## Mimari ve dosyalar

```text
Tarayıcı A -> Yerel Java istemcisi A --TCP--> Java sunucusu <--TCP-- Yerel Java istemcisi B <- Tarayıcı B
                                                ^
                                                |
                                      Yerel sunucu tarayıcısı
```

- `Main.java`: Sunucu/istemci rolüyle uygulamayı başlatır.
- `RelayServer.java`: En fazla iki istemciyi kabul eder ve mesajları yönlendirir.
- `ChatClient.java`: Sunucuya bağlanır, mesaj gönderir ve gelen mesajları dinler.
- `Protocol.java`: UTF-8 mesajları uzunluk bilgisiyle TCP üzerinden taşır.
- `WebUi.java`: Yerel web ekranını ve HTTP API'yi sunar.
- `History.java`: Son 300 kaydı bellekte tutar.
- `FileStore.java`: PDF parçalarını kaydeder, tamamlanmış dosyaları indirmeye sunar.
- `Crypto.java`: Sabit kriptografi parametreleri, anahtar üretimi, HKDF ve kimlik imzası.
- `KeyExchange.java`: Dört yöntemin anahtar paylaşımı, sunucu kimliği ve anahtar doğrulaması.
- `SecureChannel.java`: AES-GCM ile çerçeve şifreleme, bütünlük ve sıra denetimi.
- `Measurements.java`: Tamamlanan 10 tekrarlık deneylerin ortalamaları ve ham CSV sonuçları.
- `src/main/resources/web/`: HTML, CSS ve JavaScript arayüzü.
- `src/test/java/`: Gerçek TCP/HTTP bağlantılarıyla entegrasyon testi.
- `docs/PLAN.md`: Kararlar, görevler ve ilerleme planı.

Güvensiz TCP akışında her çerçeve önce 4 bayt toplam yük uzunluğu, ardından `type`, `sender`,
`text` alanlarını içerir. Her alan 4 bayt UTF-8 bayt uzunluğu ve metin baytlarından
oluşur. Kalan baytlar isteğe bağlı ham `data` alanıdır. Uzunluklar big-endian
biçimindedir. Çerçeve yükü en fazla 65.536 bayttır.
`HELLO → READY` bağlantıyı kurar; `CHAT → MESSAGE / SENT` mesajı yönlendirir.
`ERROR` ile hatalar bildirilir. `SENT`, sunucunun alıcı soketine yazabildiğini
gösterir; alıcı uygulamanın okuma/teslim onayı değildir.

Mesaj başına en fazla 4000 Java karakteri kabul edilir. Geçmiş yalnızca bellektedir;
uygulama kapanınca silinir. Arayüz durumu 800 ms aralıklarla sorgulanır.
Bu arayüz sorgulama süresi ileride kriptografik performans ölçümüne dahil edilmeyecek.
Güvenli modda bu çerçeve AES-GCM ile şifrelenip `SECURE` zarfında taşınır.
Tarayıcı sorgulama süresi mevcut ölçümlere dahil değildir.

## PDF gönderme ve indirme

1. Sunucu ve iki istemciyi güncel kodla yeniden derleyip başlatın.
   Sunucu ve istemciler **güvensiz modda** olmalıdır; güvenli PDF bu aşamada kapalıdır.
2. İki istemciyi de sunucuya bağlayın.
3. **PDF gönder** bölümünden en fazla **10 MB** bir PDF seçin ve gönderin.
4. İlerleme çubuğu sunucuya gönderilen baytları gösterir. **Alıcıya teslim edildi**
   mesajını bekleyin; yüzde 100 tek başına alıcının kaydettiği anlamına gelmez.
5. Alıcının **Alınan PDF dosyaları** bölümünden **İndir** düğmesini kullanın.
   Sunucu ekranından da sunucuda saklanan kopyayı indirebilirsiniz.

`samples/wireshark-test.pdf`, Wireshark'ta okunabilir metin içeren sıkıştırılmamış
örnek dosyadır. `scripts/create-sample-pdf.ps1` ile yeniden üretilebilir.

Dosyalar 32 KiB parçalarla `FILE_START → FILE_CHUNK → FILE_END` akışıyla taşınır.
Dosya çerçevelerinde `sender` alanı aktarım UUID'sidir. `FILE_START.text`, boyut,
satır sonu ve dosya adından oluşur; `data` alanı gönderen adını içerir. Sunucu
gönderen adını bağlantı kaydından alır. `FILE_CHUNK.data` ham PDF baytlarıdır.
Sunucu tamamını kaydettikten sonra alıcıya aynı akışla iletir. Alıcı dosyayı kaydedip
`FILE_ACK` gönderir; sunucu bu onayı göndericiye iletir. Teslim hatası `FILE_ERROR`
ile bildirilir. Sunucu 30 saniye, gönderen istemci 35 saniye teslim onayı bekler.

- Sunucu dosyaları: `runtime/server/`
- İstemci dosyaları: `runtime/client-8081/`, `runtime/client-8082/`
- Diskte dosya adı UUID'dir; indirmede özgün dosya adı kullanılır.
- Aynı adlı dosyalar birbirinin üzerine yazılmaz. Kesilen bağlantının `.part`
  dosyası temizlenir; uygulama zorla sonlandırılırsa artık `.part` dosyası kalabilir.
- Uzantı, boyut ve `%PDF-` başlığı kontrol edilir; tam PDF biçimi doğrulaması yapılmaz.
- Tamamlanan PDF'ler diskte kalır. Dosya listesi bellektedir; uygulama yeniden
  başladığında eski dosyalar ekranda listelenmez, diskten erişilebilir.
- Alıcı bağlı değilse dosya sunucuda kalır ve gönderene teslim hatası gösterilir;
  alıcı bağlanınca otomatik gönderim yapılmaz.
- `runtime/` Git'e eklenmez. Gerçek Wireshark kayıtları `captures/insecure/` altında tutulur.

## Wireshark için ilk kontrol

Tam deney, dosya adları ve Git adımları için [Wireshark rehberini](docs/WIRESHARK.md) izleyin.

Her iki bilgisayarda iletişimin geçtiği ağ arabirimini seçin. Görüntüleme filtresi:

```text
tcp.port == 5000
```

İki istemci bağlandıktan sonra ayırt edilebilir bir metin gönderin. İlgili pakette
**Follow → TCP Stream** ile açık metni inceleyin. Tek bilgisayardaki denemelerde
loopback yakalama arabirimi gerekir. Repoda güvensiz mod kayıtları bulunur;
güvenli mod için gerçek `.pcapng` kaydı henüz üretilmedi.

## Kaynaklar

- [Java HTTP sunucusu API](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html)
- [Java TCP sunucusu API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/ServerSocket.html)

..
