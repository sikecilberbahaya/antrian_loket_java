# Sistem Panggilan Loket

Aplikasi antrean loket berbasis Java Spring Boot dengan antarmuka web dan dukungan pemanggilan suara menggunakan Web Speech API. Nomor antrean dipanggil secara berurutan dari loket pertama hingga terakhir; operator dapat memilih loket, memanggil antrean berikutnya, melakukan panggilan ulang, dan menyelesaikan layanan.

## Fitur Utama

- Manajemen loket dinamis: tambah loket baru kapan saja.
- Tiket diterbitkan satu kali dan bergerak berantai antar loket sesuai urutan (mis. Loket A → B → C).
- Panggilan antrean berikutnya dan panggilan ulang dengan suara otomatis.
- Tampilan web responsif untuk memonitor nomor berjalan dan antrean tersisa.
- REST API sederhana untuk integrasi lanjutan.

## Teknologi

- Java 15
- Spring Boot 2.7.18 (Web, Validation)
- Maven
- Web Speech API (SpeechSynthesis)

## Menjalankan Aplikasi

1. Pastikan Java 15 dan Maven terpasang.
2. Dari direktori proyek, jalankan:

   ```cmd
   mvn spring-boot:run
   ```

3. Akses antarmuka web melalui `http://localhost:8080`.

## Struktur API

| Method | Endpoint                              | Deskripsi                        |
| ------ | ------------------------------------- | -------------------------------- |
| GET    | `/api/counters`                       | Daftar loket beserta statusnya.  |
| POST   | `/api/counters`                       | Tambah loket baru.               |
| POST   | `/api/tickets`                        | Terbitkan nomor antrean global.  |
| POST   | `/api/queue/call-next`                | Panggil nomor berikutnya di loket pertama. |
| POST   | `/api/counters/{id}/call-next`        | Panggil nomor siap untuk loket tertentu. |
| POST   | `/api/counters/{id}/recall`           | Panggil ulang nomor saat ini.    |
| POST   | `/api/counters/{id}/complete`         | Selesaikan layanan saat ini.     |
| GET    | `/api/queue/status`                   | Status antrean loket pertama.    |

## Testing

Jalankan pengujian unit dengan perintah berikut:

```cmd
mvn test
```

## Catatan

- Fitur suara menggunakan Web Speech API dan memerlukan browser yang mendukung (Chrome, Edge, dsb.).
- Data antrean disimpan dalam memori. Untuk kebutuhan produksi, integrasikan dengan penyimpanan persisten atau message broker sesuai kebutuhan.
