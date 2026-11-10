# İki bilgisayarda anahtar paylaşımı deneyi

Kaynak ölçümler: [koşu-1 CSV](../captures/key-exchange/anahtar-olcumleri-kosu-1.csv)
ve [koşu-2 CSV](../captures/key-exchange/anahtar-olcumleri-kosu-2.csv).
Her yöntem için her CSV'de 10 ham tekrar bulunuyor. Aşağıdaki rapor tablosu
koşu-2 CSV'sindeki 10 satırın aritmetik ortalamasıdır.

## Deney ortamı

| Alan | Bilgisayar A (sunucu) | Bilgisayar B (istemci) |
|---|---|---|
| İşlemci / model | AMD Ryzen 7 7840HS w/ Radeon 780M Graphics | 13th Gen Intel(R) Core(TM) i7-13620H (2.40 GHz) |
| RAM | 32 GB | 32 GB |
| İşletim sistemi ve sürümü | Windows 25H2 | Windows 26H2 |
| JDK (`java -version`) | 25.0.1 | 25.0.1 |
| `javac -version` | 25.0.1 | 25.0.1 |
| Ağ bağlantısı (Wi-Fi/Ethernet) | Wi-Fi | Wi-Fi |
| Güç profili | En iyi performans | En iyi performans |
| Deney tarihi ve saati | 10 Kasım 2026, 10:04–10:11 UTC | 10 Kasım 2026, 10:04–10:11 UTC |

| Ağ koşulu | Değer |
|---|---|
| Gecikme (B'den A'ya `ping -n 20 A_IP`, ortalama) | 0 ms (sonradan yapılan ping; Windows çıktısı ms tam sayıya yuvarlar) |
| Paket kaybı | 0/20 (%0; sonradan yapılan ping) |
| Sunucu IPv4 (son oktetleri rapordan çıkarabilirsiniz) | `10.16.13.228` (CSV endpoint alanı) |
| Uygulama sürümü / Git commit | `de5eaa2` (CSV'de yok; mevcut repo HEAD) |

## Koşu-2 ortalama sonuçları

| Yöntem | Tekrar | Sunucu anahtar üretimi (ms) | İstemci anahtar/ortak sır (ms) | Paylaşım (ms) | Anahtar yükü (bayt) | Toplam uygulama trafiği (bayt) |
|---|---:|---:|---:|---:|---:|---:|
| RSA-2048 | 10 | 105.6509 | 0.0363 | 167.2702 | 550.0 | 1043.5 |
| DH-2048 | 10 | 0.6675 | 1.1710 | 116.8653 | 1113.3 | 1603.4 |
| ECDH P-256 | 10 | 0.6263 | 0.7341 | 132.4717 | 182.0 | 674.1 |
| ML-KEM-768 | 10 | 0.6195 | 1.7320 | 43.1477 | 2294.0 | 2788.1 |

Koşu-2'de paylaşım süresini etkileyen yüksek örnekler vardır (DH: 507.4597 ms;
ECDH: 1034.5002 ms). Ham CSV'de korundular ve ortalamaya katıldılar. Paylaşım
süresi ağ beklemesini de kapsar; bu değerleri dışarıda bırakmak ölçümün
aritmetik ortalamasını yanlı hale getirirdi. Sonuçların bu bilgisayar ve ağ
koşullarına özgü olduğunu, gecikme sütununun ağ dalgalanmasından etkilendiğini
rapor yorumunda belirtin.

## Deney yöntemi ve sınırlamalar

Uygulama her yöntem için 10 ayrı TCP bağlantısı ve anahtar paylaşımı yürütür.
Paylaşım süresi TCP bağlantı kurulumunu içermez; istemci HELLO mesajını yazmaya
başladıktan, şifreli FINISHED_ACK doğrulanana kadar ölçülür. Süre anahtar
işlemlerini, kimlik doğrulamasını, HKDF'yi ve ağ beklemesini kapsar. Bayt
değerleri uygulama katmanındaki anahtar paylaşım çerçeveleridir; TCP/IP
başlıkları, ACK'ler ve yeniden iletimler dahil değildir. ML-KEM istemci ölçümü
kapsüllemeyi, diğer yöntemler anahtar çifti üretimini içerdiğinden işlem
sütunları bire bir aynı kriptografik işi ifade etmez.

CSV'lerde deney tarihi UTC olarak 10 Kasım 2026 ve hedef `10.16.13.228:5000`
olarak kaydedilmiş. İşletim sistemi, JDK, RAM, işlemci, ağ bağlantı türü ve güç
profili bilgileri deney ekibinin bildirdiği bilgisayar özellikleridir.
