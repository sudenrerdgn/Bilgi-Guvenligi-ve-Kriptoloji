# Güvensiz mod kayıtları - doğrulama sonucu

İki fiziksel bilgisayardaki deneyin aşağıdaki kayıtları incelendi. TShark ile
TCP akışları yeniden birleştirildi; uygulama çerçeveleri ayrıştırılarak iki yöndeki
mesajlar, PDF baytları ve alıcı kayıt onayları doğrulandı.

- [Sunucu kaydı](sunucu-metin-pdf.pcapng)
- [İstemci kaydı](captures-insecure-istemci-metin-pdf.pcapng)

| Bilgi | Sunucu | İstemci bilgisayarı |
|---|---|---|
| Kayıttaki tarih/saat (capinfos yerel çıktısı) | 16.10.2026 22:13:02–22:17:53 | 16.10.2026 22:11:33–22:17:55 |
| IPv4 | 192.168.8.9 | 192.168.8.8 |
| İşletim sistemi (kayıt metadata'sı) | Windows 11 25H2, build 26200 | Windows 11 26H2, build 26300 |
| Ağ arabirimi | Wi-Fi | Wi-Fi 2 |
| Yakalama uygulaması | Dumpcap / Wireshark 4.6.4 | Dumpcap / Wireshark 4.6.2 |
| Toplam paket | 3363 | 3768 |
| Ece TCP stream | 32 | 51 |
| Zeynep TCP stream | 34 | 52 |

Sunucu TCP portu 5000; istemci bağlantılarının kaynak portları 56041 (ece) ve
56042 (zeynep). Kullanıcı adları kayıtta küçük harfle bulunuyor.
Kayıtların tarihleri dosya içindeki zaman damgalarıdır; repoya ekleme tarihi değildir.
Deneyde kullanılan kod commit'i ve JDK sürümü kayıttan belirlenemedi.
Kayıtlar 5000 dışındaki trafiği de içeriyor; inceleme filtresi
`tcp.port == 5000` olarak uygulandı. Yakalama filtresinin tam ifadesi doğrulanmadı.

## Paketlerde görülen kanıtlar

Aşağıdaki numaralar uygulama yükünü taşıyan paketlerdir. Uzunluk alanı önceki
pakette bulunabilir; tüm çerçeve için **Follow → TCP Stream** kullanın.

| İşlem | Sunucu kaydı | İstemci kaydı |
|---|---|---|
| Ece → sunucu: GUVENSIZ-METIN-TEST-001 | 2659, stream 32 | 2238, stream 51 |
| Sunucu → Zeynep: aynı mesaj | 2665, stream 34 | 2245, stream 52 |
| Zeynep → sunucu: YANIT-002 ve satır sonu | 2716, stream 34 | 2365, stream 52 |
| Sunucu → Ece: aynı yanıt | 2722, stream 32 | 2371, stream 51 |
| Ece → sunucu: PDF | 2984, stream 32 | 2946, stream 51 |
| Sunucu → Zeynep: PDF | 2988, stream 34 | 2950, stream 52 |
| Zeynep'in FILE_ACK onayı | 2997, stream 34 | 2953, stream 52 |
| Sunucunun Ece'ye FILE_ACK iletimi | 3006, stream 32 | 2957, stream 51 |
| Zeynep → sunucu: geri gönderilen PDF | 3241, stream 34 | 3447, stream 52 |
| Sunucu → Ece: geri gönderilen PDF | 3245, stream 32 | 3456, stream 51 |
| Ece'nin FILE_ACK onayı | 3254, stream 32 | 3471, stream 51 |
| Sunucunun Zeynep'e FILE_ACK iletimi | 3259, stream 34 | 3480, stream 52 |

PDF içinde `%PDF-1.4`, `KRIPTOLOJI GUVENSIZ PDF TESTI` ve
`Bu PDF sifrelenmeden sunucu uzerinden iletilmistir.` açıkça okunuyor.

## PDF bütünlüğü

- Dosya: `samples/wireshark-test.pdf`, 681 bayt.
- İleri aktarım kimliği: `9c53c5f3-a227-4c35-bc0d-4ab2cd9b65a6`.
- Geri aktarım kimliği: `4c01d1fb-c7c5-45a0-af8f-9773ec1e2e95`.
- Her kayıtta iki aktarımın gönderici → sunucu ve sunucu → alıcı kopyaları
  çıkarıldı (kayıt başına dört PDF kopyası). Sekiz kopyanın tümü kaynakla bayt bayt aynı.
- Aynı bağlantı ve yön için iki kayıttaki yeniden birleştirilmiş TCP yükleri de aynı.

Kaynak ve ağdan çıkarılan PDF'lerin SHA-256 özeti:

```text
d03a92e6c47041240fae2671c9b500adbce75b698d018d843ea4e8e394355060
```

Alıcı kayıt onayı ağda mevcut. Tarayıcıyla indirilen yerel dosyalar bu incelemeye
verilmediği için onların disk hash'i ayrıca doğrulanmış sayılmaz.

Kayıt dosyalarının SHA-256 özetleri:

| Dosya | SHA-256 |
|---|---|
| sunucu-metin-pdf.pcapng | c1c3d34223f4daee84ec57ad5b87ec24615413aabc588a230005bf4ac6fb273c |
| captures-insecure-istemci-metin-pdf.pcapng | 22cddf650d1401e577f5e175659e8f019e6e5f12d10955d541fe7dd4e5648bb0 |

Sonuç: güvensiz modda metin ve PDF'nin ağdan okunabildiği, iki yönde aktarım ve
kayıt onayının bulunduğu doğrulandı. Güvenli mod için ayrı gerçek kayıt gerekiyor;
[AES/RSA deney adımları](../../docs/SECURE_MODE.md) aynı senaryoyu şifreli olarak uygular.
