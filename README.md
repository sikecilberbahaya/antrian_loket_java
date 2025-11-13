# Sistem Panggilan Loket

Aplikasi antrean loket berbasis Java Spring Boot dengan antarmuka web dan dukungan pemanggilan suara menggunakan Web Speech API. Nomor antrean dipanggil secara berurutan dari loket pertama hingga terakhir; operator dapat memilih loket, memanggil antrean berikutnya, melakukan panggilan ulang, dan menyelesaikan layanan.

## Fitur Utama

- Manajemen loket dinamis: tambah loket baru kapan saja.
- Tiket diterbitkan satu kali dan bergerak berantai antar loket sesuai urutan (mis. Loket A → B → C).
- Panggilan antrean berikutnya dan panggilan ulang dengan suara otomatis.
- Penomoran antrean otomatis direset setiap pukul 00.00 setiap hari.
- Tampilan web responsif untuk memonitor nomor berjalan dan antrean tersisa.
- Cetak tiket otomatis berisi nama instansi, nomor antrean, dan alamat pada setiap penerbitan nomor.
- Tanggal penerbitan tercetak di bagian bawah tiket untuk tanda terima harian.
- REST API sederhana untuk integrasi lanjutan.
- Menu pengaturan instansi untuk memperbarui nama dan alamat tanpa mengubah berkas konfigurasi.
- Tombol aktivasi suara pada display agar browser mengizinkan pemutaran audio pemanggilan.
- Setiap loket dapat menampung tiga nomor aktif sekaligus; panggilan keempat memerlukan penyelesaian salah satu nomor sebelumnya.
- Operator dapat memilih nomor aktif mana yang akan dipanggil ulang atau diselesaikan baik dari dashboard web maupun aplikasi desktop.
- Tiket dapat dihentikan (stop) dari loket sehingga tidak diteruskan ke loket berikutnya.
- Riwayat antrean tersimpan di basis data MySQL (pengambilan nomor, pemanggilan loket, dan penyelesaian/stop).

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

### Klien Desktop Loket

Setiap loket dapat menggunakan aplikasi desktop ringan untuk melakukan panggilan:

1. Bangun paket desktop:

   ```cmd
   mvn -f desktop-client/pom.xml clean package
   ```

   Artefak akan tersedia di `desktop-client/target/counter-desktop-client-0.1.0-SNAPSHOT-jar-with-dependencies.jar`.

2. Jalankan aplikasi (ganti `<loket>` dengan ID loket, mis. `A`):

   ```cmd
   java -jar desktop-client/target/counter-desktop-client-0.1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

3. Masukkan URL server (`http://alamat-server:8080`) dan ID loket pada form aplikasi, pilih nomor aktif melalui dropdown, kemudian gunakan tombol **Panggil Berikutnya**, **Panggil Ulang**, **Selesaikan**, atau **Stop** sesuai operasional loket.
4. Pengguna Windows dapat menjalankan `run-desktop-client.bat` untuk meluncurkan aplikasi dengan wizard input URL dan ID loket.

### Konfigurasi Cetak Tiket
### Konfigurasi Basis Data

Secara bawaan aplikasi terhubung ke MySQL menggunakan variabel lingkungan berikut:

```cmd
set DB_URL=jdbc:mysql://localhost:3306/panggilan_loket?useSSL=false&serverTimezone=Asia/Jakarta
set DB_USERNAME=root
set DB_PASSWORD=your_password
```

Atau ubah langsung di `src/main/resources/application.yml`. Hibernate dikonfigurasi dengan `ddl-auto=update`; sesuaikan strategi ini untuk lingkungan produksi.


Atur nama instansi dan alamat yang tercetak melalui `src/main/resources/application.yml`:

```yaml
printer:
   ticket:
      enabled: true
      institution-name: "Klinik Sehat Sentosa"
      address: "Jalan Contoh No. 123, Kota"
```

Pastikan printer default pada sistem telah terpasang; aplikasi akan melewati pencetakan jika tidak menemukan printer.

Anda juga dapat memperbarui nama instansi dan alamat secara cepat melalui `settings.html` di dashboard utama.

#### Cara Mengoperasikan Klien Desktop

- **Pilih Loket & Server**: masukkan URL backend (contoh `http://localhost:8080`) dan ID loket (`A`, `B`, `C`, dll.), lalu simpan. Pengaturan tersimpan selama aplikasi berjalan.
- **Panggil Berikutnya**: mengambil nomor antrean siap untuk loket tersebut. Jika belum ada nomor yang menunggu, tombol akan nonaktif otomatis.
- **Panggil Ulang**: pilih nomor aktif pada dropdown lalu putar ulang untuk memastikan pemanggilan terdengar jelas di area tunggu.
- **Selesaikan**: pilih nomor aktif pada dropdown lalu tandai selesai agar nomor berpindah ke loket berikutnya (atau selesai sepenuhnya bila loket terakhir).
- **Stop**: pilih nomor aktif lalu hentikan tanpa meneruskan ke loket berikutnya.
- **Pembaruan Status**: panel status di bagian kiri menampilkan nomor aktif dan estimasi antrean; data diperbarui otomatis setiap beberapa detik.
- **Gangguan Koneksi**: jika koneksi backend terputus, aplikasi menampilkan peringatan; periksa jaringan dan tekan kembali tombol sesuai kebutuhan setelah koneksi normal.

## Struktur API

| Method | Endpoint                              | Deskripsi                        |
| ------ | ------------------------------------- | -------------------------------- |
| GET    | `/api/counters`                       | Daftar loket beserta statusnya.  |
| POST   | `/api/counters`                       | Tambah loket baru.               |
| POST   | `/api/tickets`                        | Terbitkan nomor antrean global.  |
| POST   | `/api/queue/call-next`                | Panggil nomor berikutnya di loket pertama. |
| POST   | `/api/counters/{id}/call-next`        | Panggil nomor siap untuk loket tertentu. |
| POST   | `/api/counters/{id}/recall`           | Panggil ulang nomor aktif tertentu (`ticketId` opsional). |
| POST   | `/api/counters/{id}/complete`         | Selesaikan layanan aktif tertentu (`ticketId` opsional). |
| POST   | `/api/counters/{id}/stop`             | Hentikan nomor aktif tertentu tanpa meneruskan (`ticketId` opsional). |
| GET    | `/api/queue/status`                   | Status antrean loket pertama.    |

## Testing

Jalankan pengujian unit dengan perintah berikut:

```cmd
mvn test
```

## Catatan

- Fitur suara menggunakan Web Speech API dan memerlukan browser yang mendukung (Chrome, Edge, dsb.).
- Data antrean disimpan dalam memori. Untuk kebutuhan produksi, integrasikan dengan penyimpanan persisten atau message broker sesuai kebutuhan.
