const takeButtonLama = document.getElementById("take-ticket-lama");
const takeButtonBaru = document.getElementById("take-ticket-baru");
const issuedNumberElement = document.getElementById("issued-number");
const patientTypeLabelElement = document.getElementById("patient-type-label");
const feedbackElement = document.getElementById("ticket-feedback");
const nextNumberLamaElement = document.getElementById("public-next-number-lama");
const nextNumberBaruElement = document.getElementById("public-next-number-baru");
const queueLengthElement = document.getElementById("public-queue-length");

let refreshTimer;

async function refreshQueueStatus() {
    try {
        const response = await fetch("/api/queue/status");
        if (!response.ok) {
            throw new Error("Gagal memuat status antrean");
        }
        const status = await response.json();
        
        // Count waiting by patient type
        let lamaCount = 0;
        let baruCount = 0;
        if (Array.isArray(status.waitingQueue)) {
            status.waitingQueue.forEach(ticket => {
                if (ticket.patientType === "BARU") {
                    baruCount++;
                } else {
                    lamaCount++;
                }
            });
        }
        
        // Update next numbers - estimate based on prefix pattern
        nextNumberLamaElement.textContent = formatTicketNumber("L", lamaCount + 1);
        nextNumberBaruElement.textContent = formatTicketNumber("B", baruCount + 1);
        
        queueLengthElement.textContent = Array.isArray(status.waitingQueue) ? status.waitingQueue.length : 0;
    } catch (error) {
        console.error(error);
        showFeedback(error.message, true);
    }
}

async function takeTicket(patientType) {
    try {
        const response = await fetch(`/api/tickets?patientType=${patientType}`, { method: "POST" });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || "Gagal mengambil nomor antrean");
        }
        const ticket = await response.json();
        issuedNumberElement.textContent = ticket.number;
        
        // Show patient type label
        const typeLabel = patientType === "BARU" ? "Pasien Baru" : "Pasien Lama";
        patientTypeLabelElement.textContent = typeLabel;
        patientTypeLabelElement.classList.remove("hidden");
        
        showFeedback(`Nomor antrean Anda ${ticket.number} (${typeLabel}). Silakan menunggu panggilan ke loket.`, false);
    } catch (error) {
        console.error(error);
        showFeedback(error.message, true);
    } finally {
        await refreshQueueStatus();
    }
}

takeButtonLama.addEventListener("click", () => takeTicket("LAMA"));
takeButtonBaru.addEventListener("click", () => takeTicket("BARU"));

function formatTicketNumber(prefix, sequence) {
    return `${prefix}-${String(sequence).padStart(3, "0")}`;
}

function showFeedback(message, isError = false) {
    if (!message) {
        feedbackElement.classList.add("hidden");
        return;
    }
    feedbackElement.textContent = message;
    feedbackElement.className = `feedback ${isError ? "error" : ""}`;
    feedbackElement.classList.remove("hidden");
}

window.addEventListener("load", () => {
    refreshQueueStatus();
    refreshTimer = setInterval(refreshQueueStatus, 5000);
});

window.addEventListener("beforeunload", () => {
    if (refreshTimer) {
        clearInterval(refreshTimer);
    }
});
