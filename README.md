# Bilgi Güvenliği ve Kriptoloji

Java sunucusu ve iki Java istemcisinden oluşan, Türkçe web arayüzlü ders projesi.
Her uygulama kendi bilgisayarında çalışır; arayüzü o bilgisayarın tarayıcısında açılır.

**Mevcut aşama:** Sunucu üzerinden güvensiz modda metin iletişimi. PDF aktarımı,
AES, anahtar paylaşımı ve performans ölçümleri henüz uygulanmadı.

## Görev Dağılımı:
| Üye | Ana sorumluluk | Raporda anlatacağı bölüm |
|---|---|---|
| Sudenur Erdoğan | Java sunucusu, bağlantıları yönetme, mesaj ve PDF yönlendirme, sunucu ekranı | Sistem mimarisi ve sunucunun çalışma akışı |
| Ece Naz Genç| RSA, DH, ECDH, ML-KEM ile anahtar paylaşımı; AES ile şifreleme/çözme | Güvenli mod ve anahtar dağıtım yöntemleri |
| Zeynep Yılmaz| İstemci web arayüzü, mesaj geçmişi, dosya seçimi, aktarım durumu ve ölçüm sonuçlarının gösterimi | İstemci kullanımı ve karşılaştırma sonuçları |

## Gereksinimler

- Windows, VS Code ve JDK 21 veya üzeri (`java` ve `javac` komutları erişilebilir olmalı).
- İlk aşamada Maven, Gradle veya harici Java kütüphanesi gerekmiyor.
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
- `src/main/resources/web/`: HTML, CSS ve JavaScript arayüzü.
- `src/test/java/`: Gerçek TCP/HTTP bağlantılarıyla entegrasyon testi.
- `docs/PLAN.md`: Kararlar, görevler ve ilerleme planı.

TCP akışında her çerçeve önce 4 bayt toplam yük uzunluğu, ardından `type`, `sender`,
`text` alanlarını içerir. Her alan 4 bayt UTF-8 bayt uzunluğu ve metin baytlarından
oluşur. Uzunluklar big-endian biçimindedir. Çerçeve en fazla 65.536 bayttır.
`HELLO → READY` bağlantıyı kurar; `CHAT → MESSAGE / SENT` mesajı yönlendirir.
`ERROR` ile hatalar bildirilir. `SENT`, sunucunun alıcı soketine yazabildiğini
gösterir; alıcı uygulamanın okuma/teslim onayı değildir.

Mesaj başına en fazla 4000 Java karakteri kabul edilir. Geçmiş yalnızca bellektedir;
uygulama kapanınca silinir. Arayüz durumu 800 ms aralıklarla sorgulanır.
Bu arayüz sorgulama süresi ileride kriptografik performans ölçümüne dahil edilmeyecek.

## Wireshark için ilk kontrol

Her iki bilgisayarda iletişimin geçtiği ağ arabirimini seçin. Görüntüleme filtresi:

```text
tcp.port == 5000
```

İki istemci bağlandıktan sonra ayırt edilebilir bir metin gönderin. İlgili pakette
**Follow → TCP Stream** ile açık metni inceleyin. Tek bilgisayardaki denemelerde
loopback yakalama arabirimi gerekir. Henüz `.pcapng` kaydı üretilmedi.

## Kaynaklar

- [Java HTTP sunucusu API](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html)
- [Java TCP sunucusu API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/ServerSocket.html)


