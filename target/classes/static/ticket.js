const takeButton = document.getElementById("take-ticket");
const issuedNumberElement = document.getElementById("issued-number");
const feedbackElement = document.getElementById("ticket-feedback");
const nextNumberElement = document.getElementById("public-next-number");
const queueLengthElement = document.getElementById("public-queue-length");

let refreshTimer;

async function refreshQueueStatus() {
    try {
        const response = await fetch("/api/queue/status");
        if (!response.ok) {
            throw new Error("Gagal memuat status antrean");
        }
        const status = await response.json();
        if (Number.isInteger(status.nextTicketNumber) && status.nextTicketNumber > 0) {
            nextNumberElement.textContent = formatTicketNumber(status.nextTicketNumber);
        } else {
            nextNumberElement.textContent = "-";
        }
        queueLengthElement.textContent = Array.isArray(status.waitingQueue) ? status.waitingQueue.length : 0;
    } catch (error) {
        console.error(error);
        showFeedback(error.message, true);
    }
}

takeButton.addEventListener("click", async () => {
    try {
        const response = await fetch("/api/tickets", { method: "POST" });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || "Gagal mengambil nomor antrean");
        }
        const ticket = await response.json();
        issuedNumberElement.textContent = ticket.number;
        showFeedback(`Nomor antrean Anda ${ticket.number}. Silakan menunggu panggilan ke loket.`, false);
    } catch (error) {
        console.error(error);
        showFeedback(error.message, true);
    } finally {
        await refreshQueueStatus();
    }
});

function formatTicketNumber(sequence) {
    return `Q-${String(sequence).padStart(3, "0")}`;
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
