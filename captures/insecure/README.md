# Güvensiz mod kayıtları

Henüz gerçek ağ kaydı alınmadı. Bu klasöre iki fiziksel bilgisayarda aynı deney
sırasında alınan kayıtlar eklenecek:

- `sunucu-metin-pdf.pcapng`
- `istemci-metin-pdf.pcapng`

İki kayıtta da istemcilerin bağlanması, iki yönde metin ve PDF iletimi bulunmalı.
[Adım adım Wireshark rehberi](../../docs/WIRESHARK.md) izlenebilir.

Kayıt sonrasında aşağıdaki alanları gerçek bilgilerle doldurun:

| Bilgi | Değer |
|---|---|
| Deney tarihi ve saati | Henüz alınmadı |
| Kod commit kimliği (`git rev-parse HEAD`) | |
| Sunucu IPv4 ve işletim sistemi | |
| İstemci bilgisayarı IPv4 ve işletim sistemi | |
| Sunucuda seçilen ağ arabirimi | |
| İstemcide seçilen ağ arabirimi | |
| Wireshark sürümleri | |
| TCP portu | 5000 |
| Yakalama filtresi | `tcp port 5000` |
| Gönderilen PDF | `samples/wireshark-test.pdf` |
| Kaynak PDF SHA-256 | |
| İndirilen PDF SHA-256 | |
| Metnin görüldüğü TCP stream / paket numarası | |
| PDF içeriğinin görüldüğü TCP stream / paket numarası | |
| Sonuç / karşılaşılan sorun | |
