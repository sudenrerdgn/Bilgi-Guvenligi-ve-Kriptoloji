# Ece'nin haftalık çalışması: AES-128 ve RSA-2048

Bu haftanın kapsamı Java kütüphanesiyle AES-128-GCM, RSA-2048 ile ilk anahtar
paylaşımı, şifreli mesaj/PDF aktarımı ve testlerdir. Güncel projede DH, ECDH ve
ML-KEM de mevcut; ilk gösterimde iki istemcide RSA seçilmesi yeterlidir.
Bu rehber ilk AES/RSA haftasını anlatır. Sonraki çalışma olan
[manuel AES-128, anahtar genişletme ve turlar](MANUAL_AES.md) ayrıca tamamlandı.

## Sunumda kısa anlatım

“Mesaj ve PDF içeriğini AES-128-GCM ile şifreliyoruz. AES simetrik olduğu için
iki tarafın aynı anahtarı bilmesi gerekir. İlk bağlantıda istemci 16 bayt
rastgele bir başlangıç sırrı üretip sunucunun RSA-2048 açık anahtarıyla şifreler.
Sunucu bunu kendi özel anahtarıyla çözer. İki taraf bu sırdan ve el sıkışmasının
özetinden HKDF ile yönlere ayrı AES-128 anahtarları türetir. Sonraki mesajlar,
PDF parçaları ve teslim onayları bu anahtarlarla korunur. Sunucu içeriği çözüp
diğer istemcinin ayrı bağlantı anahtarıyla yeniden şifreler.”

## Anahtar paylaşımı nasıl ilerliyor?

1. İstemci RSA yöntemini, kullanıcı adını ve yeni rastgele değerini gönderir.
2. Sunucu bu bağlantı için RSA-2048 anahtar çifti üretir. Açık anahtarını,
   rastgele değerini ve imzalı yanıtını istemciye yollar; RSA özel anahtarı gönderilmez.
3. İstemci, sunucu ekranından alınan SHA-256 parmak izini ve Ed25519 imzasını
   doğrular. Böylece başka bir sunucunun anahtarını kabul etmez.
4. İstemci `SecureRandom` ile 16 bayt sır üretir; RSA-OAEP ile şifreler.
   OAEP ve MGF1 için SHA-256 açıkça seçilmiştir. RSA şifreli çıktı 256 bayttır.
5. Sunucu RSA özel anahtarıyla bu sırrı çözer. HKDF-SHA256, el sıkışma özetini
   de kullanarak istemci → sunucu ve sunucu → istemci için iki farklı 16 bayt
   AES anahtarı üretir. Başlangıç sırrı doğrudan trafik anahtarı olarak kullanılmaz.
6. Şifreli `FINISHED` ve `FINISHED_ACK` mesajlarıyla iki tarafın aynı anahtarları
   elde ettiği doğrulanır. Ardından `READY` gelir ve sohbet başlar.

RSA burada büyük dosyaları şifrelemek için kullanılmaz; bağlantı sırrını taşır.
PDF parçalarını AES şifreler. RSA ve AES uygulamaları Java JCA/JCE'den gelir;
HKDF akışı Java'nın `HmacSHA256` işlemi üzerine kurulmuştur.

## AES-128-GCM neyi koruyor?

| Parça | Bu projedeki seçim |
|---|---|
| AES anahtarı | Her yön için 128 bit / 16 bayt |
| Java dönüşümü | `AES/GCM/NoPadding` |
| Nonce | 96 bit: 4 bayt oturum/yön ön eki + 8 bayt artan sayaç |
| Doğrulama etiketi | 128 bit; değiştirilmiş içerik reddedilir |
| AAD | Protokol etiketi ve nonce; şifrelenmeden doğrulamaya katılır |
| Tekrar/sıra kontrolü | Beklenen sayaçla eşleşmeyen kayıt reddedilir |
| PDF parçası | En fazla 32 KiB; her parça ayrı GCM kaydı |

Aynı anahtarla aynı nonce tekrar kullanılmamalıdır. Bu yüzden yazma işlemleri
aynı kilit altında sıralanır; metin, PDF ve onaylar ortak kanal sayacını kullanır.
Yeni bağlantıda yeni el sıkışma ve yeni anahtarlar oluşturulur.
GCM etiketi doğrulanmadan çözülen parça dosyaya yazılmaz.

Dosya adı, aktarım kimliği, PDF baytları, başlangıç/bitiş ve teslim onayı
`SECURE` zarfının içindedir. Ağda zarf türü, boyutlar, zamanlama ve ilk el
sıkışmasının açık bilgileri görülebilir. Sunucu ve alıcı dosyayı açık halde
kaydeder. Bu tasarım istemci–sunucu şifrelemesidir; uçtan uca şifreleme değildir.

## Kodda göstereceğin yerler

| Dosya / metot | Anlatılacak iş |
|---|---|
| `Crypto.generate("RSA")` | 2048 bit RSA anahtar çifti |
| `Crypto.rsa` | OAEP SHA-256 / MGF1-SHA256 ile şifreleme ve çözme |
| `KeyExchange.client/server` | Parmak izi, imza, sır paylaşımı ve şifreli onaylar |
| `SecureChannel.activate` | Yönlere ayrı AES anahtarları ve nonce ön ekleri |
| `SecureChannel.write/read/crypt` | GCM şifreleme, etiket ve sayaç kontrolü |
| `FileStore.transmit` | Dosya çerçevelerinin aynı kanaldan gönderilmesi |
| `RelayServer.forwardFile` | Sunucudan alıcının kanalıyla yeniden şifreleme |

## Beş dakikalık gösterim

1. [Çalıştırma rehberi](SECURE_MODE.md) ile sunucu ve iki istemciyi başlat.
2. Sunucuyu güvenli moda geçir; iki istemcide Güvenli / RSA-2048 seç ve
   sunucu parmak izini gir.
3. İki yönde Türkçe mesaj ve örnek PDF gönder. Alıcı onayını göster ve PDF'yi indir.
4. Sunucu kopyasının okunabildiğini, ağda ise mesaj/PDF'nin şifreli taşındığını açıkla.
5. Bağlantıyı kesip RSA için **Seçili yöntemle 10 ölçüm yap** düğmesini çalıştır;
   ortalamayı göster, ham CSV'yi indir.

Ölçüm tablosu anahtar paylaşımını ölçer; PDF aktarım süresi veya AES'in tek başına
hızı değildir. Paylaşım süresi anahtar üretimi, doğrulama ve ağ beklemesini de içerir.
Tam sınırlar [ölçüm rehberinde](SECURE_MODE.md#4-süre-ve-boyutları-karşılaştırın).

## Doğrulama

`scripts/test.ps1` ile 21 temel iletişim, 40 PDF ve 88 güvenli mod kontrolü
geçti (toplam 149). Testler gerçek yerel TCP/HTTP bağlantıları kullanır:

- RSA ile iki yönlü şifreli mesaj ve PDF, HTTP yükleme/indirme.
- 10 MiB dosyanın bayt bayt korunması; eşzamanlı PDF aktarımı.
- Yanlış sunucu kimliği, değiştirilmiş GCM etiketi ve tekrar kaydının reddi.
- Bozuk PDF parçasında bağlantının kapanması ve yarım dosyanın temizlenmesi.
- Dört yöntemde PDF aktarımı; ağ çerçevelerinde açık PDF adı/içeriği bulunmaması.
- Onarlı ölçümler, CSV ve ortalamaların tutarlılığı.

Güvensiz modun iki bilgisayarlı [Wireshark kayıtları doğrulandı](../captures/insecure/README.md).
Güvenli modun iki fiziksel bilgisayarda Wireshark kaydı hâlâ alınmalıdır.

## Kaynaklar

- [Java Cipher / AES-GCM](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Cipher.html)
- [Java OAEP parametreleri](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/spec/OAEPParameterSpec.html)
- [RFC 5869: HKDF](https://www.rfc-editor.org/rfc/rfc5869.html)
