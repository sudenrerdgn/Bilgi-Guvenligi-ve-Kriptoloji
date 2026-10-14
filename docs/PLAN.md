# Proje kararları ve ilerleme

## Kabul edilen kararlar

- Java; Windows; VS Code; en az iki fiziksel bilgisayar.
- Bir Java sunucusu ve iki Java istemcisi, her uygulama için yerel web arayüzü.
- İstemciler doğrudan bağlanmaz; eşzamanlı mesajlaşma desteklenir.
- Sunucu ekranı ayrı tarayıcı penceresinde açılır.
- Türkçe arayüz; Türkçe ancak Türkçe karakter içermeyen kod yorumları.
- İngilizce sınıf, değişken ve fonksiyon isimleri.
- Orta seviyede, sunumda açıklanabilir bir yapı; geliştirme adım adım yapılır.
- PDF sunucuda otomatik kaydedilir, istemcide İndir düğmesiyle alınır (planlandı).

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
- [ ] PDF aktarımı, sunucuda kayıt, istemcide indirme ve ilerleme
- [ ] AES-128 ile güvenli mod
- [ ] RSA-2048, DH-2048, ECDH ve ML-KEM-768
- [ ] Manuel AES ve aynı girdide kütüphane karşılaştırması
- [ ] Her yöntem için 10 tekrar, ortalamalar ve boyut tablosu
- [ ] Sunucu ve istemci tarafında Wireshark kayıtları
- [ ] Rapor, README'nin son hali ve sunum

## Kriptografi aşamasında netleştirilecekler

AES çalışma modu, IV/nonce ve doğrulama yaklaşımı; RSA padding; DH grubu;
ECDH eğrisi; ortak sırdan AES-128 anahtarı türetme yöntemi; ML-KEM kütüphanesi;
manuel AES'in haberleşmede kullanımı; zaman ve bayt ölçümlerinin kesin sınırları.
Güvenli mod istemci–sunucu bağlantıları içindir: sunucu içeriği çözer ve hedef
istemcinin anahtarıyla yeniden şifreler. Uçtan uca şifreleme olarak sunulmaz.
