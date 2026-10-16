# Güvensiz modda ilk Wireshark kaydı

Bu deney, en az iki **fiziksel bilgisayarda** yapılır. Sunucu ve istemci tarafında
aynı deneyin ayrı `.pcapng` kayıtları alınır. Tek bilgisayar/loopback testi bunun
yerine geçmez. Aşağıdaki kayıtlar henüz alınmış değildir.

## 1. Uygulamaları hazırlayın

İki bilgisayarda aynı güncel kod bulunmalı. Eski Java uygulamalarını `Ctrl+C` ile
kapatıp her iki bilgisayarda proje klasöründen derleyin:

```powershell
.\scripts\build.ps1
```

Sunucu ve istemcilerin tamamını güncelleyin; PDF için protokole ham bayt alanı eklendi.
PowerShell betikleri engelliyorsa komutu şu biçimde çalıştırın:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1
```

**Bilgisayar A — sunucu:**

```powershell
.\scripts\run.ps1 -Role server -WebPort 8080 -TcpPort 5000
```

Sunucu ekranı A'da `http://127.0.0.1:8080` adresinde açılır. Ayrı terminalde
`ipconfig` çalıştırın. Kullandığınız Wi-Fi/Ethernet bağlantısının IPv4 adresini not
edin; örnek: `192.168.1.105`. VPN, sanal adaptör veya `127.0.0.1` adresini seçmeyin.

**Bilgisayar B — iki ayrı terminalde istemciler:**

```powershell
.\scripts\run.ps1 -Role client -WebPort 8081
```

```powershell
.\scripts\run.ps1 -Role client -WebPort 8082
```

B'de `http://127.0.0.1:8081` ve `http://127.0.0.1:8082` adreslerini açın.
Sunucu IP alanına **A'nın gerçek IPv4 adresini**, porta `5000`, kullanıcı adlarına
`Ece` ve `Zeynep` yazın. Henüz Bağlan düğmesine basmayın.

Üç bilgisayar kullanıyorsanız her istemci ayrı bilgisayarda 8081 portuyla çalışabilir.
Sunucuda ve istemci bilgisayarlarından en az birinde kayıt alın; üçüncü kayıt da eklenebilir.

## 2. Her iki bilgisayarda Wireshark'ı başlatın

1. **Capture → Options** menüsünü açın.
2. Aynı yerel ağa bağlı **Wi-Fi** veya **Ethernet** arabirimini seçin. Arabirimin
   IPv4 adresinin `ipconfig` çıktısındaki adresle eşleştiğini kontrol edin.
3. **Capture Filter** alanına `tcp port 5000` yazın. Bu, yalnızca uygulamanın TCP
   trafiğini kayda alır. Web ekranlarının yerel 8080/8081/8082 trafiği bu kayda girmez.
4. Paketleri kırpmamak için varsayılan tam paket yakalama uzunluğunu koruyun.
5. İki bilgisayarda da **Start** düğmesine basın.

Yakalama filtresi `tcp port 5000`, üstteki görüntüleme filtresi ise
`tcp.port == 5000` şeklindedir; bu iki alanın yazımı farklıdır.
Arabirim ve filtre ayarları [resmî Capture Options rehberinde](https://www.wireshark.org/docs/wsug_html_chunked/ChCapCaptureOptions.html),
yakalama filtreleri [Wireshark Wiki'de](https://wiki.wireshark.org/captureFilters) açıklanır.

## 3. Kaydı durdurmadan deneyi yapın

1. B'deki iki istemcide **Sunucuya bağlan** düğmesine basın. Sunucuda `2 / 2` görünmeli.
2. Birinci istemciden `GUVENSIZ-METIN-TEST-001` gönderin.
3. Diğer istemciden `YANIT-002` gönderin.
4. Birinci istemcinin **PDF gönder** bölümünden repodaki
   `samples/wireshark-test.pdf` dosyasını seçip gönderin.
5. Göndericide **Alıcıya teslim edildi**, sunucuda **PDF sunucuya kaydedildi**
   kaydını bekleyin. Alıcıda **Alınan PDF dosyaları → İndir** düğmesine basıp açın.
6. Aynı PDF'yi diğer istemciden geri gönderin; iki yönü de doğrulayın.
7. İstemcilerde **Bağlantıyı kes** düğmesine basın; bağlantı kapanışları da kayda girsin.
8. İki Wireshark penceresinde kırmızı kare **Stop** düğmesine basın.

İlerleme çubuğu sunucuya gönderilen baytları gösterir. Yüzde 100, tek başına teslim
anlamına gelmez; alıcının dosyayı kaydedip `FILE_ACK` göndermesi ayrıca beklenir.

İsteğe bağlı dosya bütünlüğü kontrolü: kaynak PDF ve alıcıda indirilen PDF için
aşağıdaki komutu ayrı ayrı çalıştırıp Hash değerlerinin aynı olduğunu doğrulayın:

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath .\samples\wireshark-test.pdf
```

İndirilen dosya için `-LiteralPath` değerine gerçek indirme yolunu yazın.

## 4. Açık metni ve PDF'yi gösterin

1. Üstteki görüntüleme filtresine `tcp.port == 5000` yazın.
2. Veri taşıyan bir TCP paketine sağ tıklayın: **Follow → TCP Stream**.
3. **Show data as: UTF-8** veya **ASCII** seçin. `GUVENSIZ-METIN-TEST-001`,
   `FILE_START`, `%PDF-1.4` ve `KRIPTOLOJI GUVENSIZ PDF TESTI` metinlerini bulun.
4. Aradığınız içerik yoksa pencerenin stream seçicisinden diğer bağlantıyı inceleyin.
   Bir istemci bağlantısı gönderici→sunucu, diğeri sunucu→alıcı yolunu gösterir.
5. Metin ve PDF içeriğinin görüldüğü ekran görüntülerini rapor için saklayın.

Örnek PDF özellikle sıkıştırılmadan üretildi. Başka PDF'lerin metni kendi iç
sıkıştırması yüzünden okunmayabilir; bu durum ağda şifrelendiği anlamına gelmez.
PDF dosya baytları bu uygulamada Base64 veya şifreleme olmadan taşınır.

`FILE_CHUNK` parçaları 32 KiB'dır; bir uygulama parçası bir TCP paketiyle aynı şey
değildir. TCP parçalayabilir veya birleştirebilir. Ayrıca protokol başlıkları nedeniyle
**Follow TCP Stream → Save As** çıktısı doğrudan açılabilir bir PDF değildir.
Asıl PDF'yi uygulamanın **İndir** düğmesinden alın.

[Resmî Follow Stream rehberi](https://www.wireshark.org/docs/wsug_html_chunked/ChAdvFollowStreamSection.html)
görünüm, arama ve stream seçimini açıklar.

## 5. .pcapng dosyalarını kaydedin

Wireshark'ın **ana penceresinde File → Save As** kullanın; Follow TCP Stream
penceresindeki Save As düğmesini kullanmayın. Dosya biçimi **pcapng** olsun.

- A'daki kaydı `captures/insecure/sunucu-metin-pdf.pcapng` adıyla kaydedin.
- B'deki kaydı `captures/insecure/istemci-metin-pdf.pcapng` adıyla kaydedin.
- İki dosyayı aynı repoda bir araya getirin. Her dosyayı Wireshark'ta yeniden açıp
  paketlerin, test metninin ve PDF içeriğinin bulunduğunu kontrol edin.

Bu adımlar [resmî kayıt rehberinde](https://www.wireshark.org/docs/wsug_html_chunked/ChIOSaveSection.html)
açıklanan pcapng kaydını kullanır. Metin/PDF tek deneyde birlikte kaydedilebilir.

## 6. Repoya ekleyin

`captures/insecure/README.md` tablosunu gerçek IP, tarih, Wireshark sürümü,
commit kimliği ve hash sonuçlarıyla doldurun. Sonra:

```powershell
git add captures/insecure
git commit -m "Guvensiz mod metin ve PDF Wireshark kayitlarini ekle"
git push
```

Kod değişikliklerini de grubun normal Git akışıyla paylaşın. `runtime/` kayıt
klasörü Git tarafından dışlanır; `.pcapng` dosyalarını o klasöre koymayın.
Henüz alınmamış bir kayıt için teslim edildi bilgisi veya örnek `.pcapng` üretilmez.

## Bağlantı kurulamazsa

- Sunucunun çalıştığını ve doğru IPv4 adresinin yazıldığını kontrol edin.
- B'den `Test-NetConnection SUNUCU_IP -Port 5000` çalıştırın; `SUNUCU_IP` yerine
  gerçek IPv4 adresini yazın. `TcpTestSucceeded` değeri `True` olmalı.
- Windows Güvenlik Duvarı'nda kullanılan özel ağ için Java/TCP 5000 gelen
  bağlantısına izin verin; güvenlik duvarını tümüyle kapatmayın.
- Misafir Wi-Fi veya istemci izolasyonu cihazlar arası bağlantıyı engelleyebilir.
- Ekran çalışıp paket görünmüyorsa seçilen arabirimi kontrol edin. Uzak sunucu
  IP alanında `127.0.0.1` yazıyorsa aynı bilgisayara bağlanmaya çalışıyorsunuzdur.

Güvenli mod eklenince güvensiz mod seçeneği korunacak. İlk kaydı şimdi almak,
iki modu aynı senaryoyla karşılaştırabileceğiniz bir başlangıç kaydı sağlar.
