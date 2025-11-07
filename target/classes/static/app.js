const counterListElement = document.getElementById("counter-list");
const queueItemsElement = document.getElementById("queue-items");
const nextNumberElement = document.getElementById("next-number");
const queueLengthElement = document.getElementById("queue-length");
const feedbackElement = document.getElementById("feedback");
const counterForm = document.getElementById("counter-form");

const voiceEnabled = "speechSynthesis" in window;
let refreshTimer;

async function loadStatus() {
    try {
        const [countersResponse, queueResponse] = await Promise.all([
            fetch("/api/counters"),
            fetch("/api/queue/status")
        ]);

        if (!countersResponse.ok) {
            throw new Error("Gagal memuat data loket");
        }
        if (!queueResponse.ok) {
            throw new Error("Gagal memuat data antrean");
        }

        const counters = await countersResponse.json();
        const queueStatus = await queueResponse.json();

    renderCounters(counters);
        renderQueue(queueStatus.waitingQueue);
        updateNextNumber(queueStatus.nextTicketNumber);
    } catch (error) {
        console.error(error);
        showFeedback(error.message, true);
    }
}

function renderCounters(counters) {
    counterListElement.innerHTML = "";
    counters.forEach(counter => {
        const waitingTickets = Array.isArray(counter.waitingTickets) ? counter.waitingTickets : [];
        const card = document.createElement("article");
        card.className = "counter-card";
        card.innerHTML = `
            <div class="counter-header">
                <h3>${counter.name}</h3>
                <span class="secondary-text">ID: ${counter.id}</span>
            </div>
            <div>
                <p class="secondary-text">Sedang Dilayani</p>
                <p class="current-ticket">${counter.currentTicket ? counter.currentTicket.number : "-"}</p>
            </div>
            <div class="actions">
                <button data-action="call" data-counter="${counter.id}">Panggil Selanjutnya</button>
                <button data-action="recall" data-counter="${counter.id}">Panggil Ulang</button>
                <button data-action="complete" data-counter="${counter.id}">Selesaikan</button>
            </div>
            <div class="waiting-list">
                <strong>Antrean Menunggu</strong>
                <ul>
                    ${waitingTickets.length === 0 ? "<li>-</li>" : waitingTickets.map(ticket => `<li>${ticket.number}</li>`).join("")}
                </ul>
            </div>
        `;
        card.querySelectorAll("button").forEach(button => {
            button.addEventListener("click", handleCounterAction);
        });
        counterListElement.appendChild(card);
    });
}

function renderQueue(queue) {
    queueItemsElement.innerHTML = "";
    const items = Array.isArray(queue) ? queue : [];
    if (queueLengthElement) {
        queueLengthElement.textContent = items.length;
    }
    if (items.length === 0) {
        queueItemsElement.innerHTML = "<li>-</li>";
        return;
    }
    items.forEach(ticket => {
        const item = document.createElement("li");
        item.textContent = ticket.number;
        queueItemsElement.appendChild(item);
    });
}

function updateNextNumber(nextNumber) {
    if (!Number.isInteger(nextNumber) || nextNumber < 1) {
        nextNumberElement.textContent = "-";
        return;
    }
    nextNumberElement.textContent = formatTicketNumber(nextNumber);
}

async function handleCounterAction(event) {
    const action = event.currentTarget.dataset.action;
    const counterId = event.currentTarget.dataset.counter;
    let endpoint;

    if (action === "call") {
        endpoint = `/api/counters/${counterId}/call-next`;
    } else if (action === "recall") {
        endpoint = `/api/counters/${counterId}/recall`;
    } else if (action === "complete") {
        endpoint = `/api/counters/${counterId}/complete`;
    } else {
        return;
    }

    try {
        const response = await fetch(endpoint, { method: "POST" });
        if (response.status === 204) {
            const message = action === "complete"
                ? `Loket ${counterId} tidak memiliki nomor untuk diselesaikan.`
                : `Tidak ada antrean untuk loket ${counterId}.`;
            showFeedback(message, true);
            return;
        }
        if (!response.ok) {
            let message = "Terjadi kesalahan";
            try {
                const error = await response.json();
                message = error.error || message;
            } catch (parseError) {
                // Abaikan kegagalan parsing dan pertahankan pesan umum.
            }
            throw new Error(message);
        }
        if (action === "complete") {
            showFeedback(`Loket ${counterId} selesai melayani nomor saat ini.`);
        } else {
            const ticket = await response.json();
            announceTicket(ticket, action === "recall");
            const verb = action === "recall" ? "Panggilan ulang" : "Memanggil";
            showFeedback(`${verb} nomor ${ticket.number} ke ${ticket.counterName}.`);
        }
    } catch (error) {
        console.error(error);
        showFeedback(error.message, true);
    } finally {
        await loadStatus();
    }
}

function announceTicket(ticket, isRecall = false) {
    if (!voiceEnabled || !ticket) {
        return;
    }
    const sentence = `${isRecall ? "Panggilan ulang untuk" : "Nomor antrean"} ${spellTicket(ticket.number)} menuju ${ticket.counterName}`;
    const utterance = new SpeechSynthesisUtterance(sentence);
    utterance.lang = "id-ID";
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utterance);
}

function spellTicket(ticketNumber) {
    const parts = ticketNumber.split("-");
    if (parts.length !== 2) {
        return ticketNumber;
    }
    const prefix = parts[0].split("").join(" ");
    const digits = parts[1].split("").join(" ");
    return `${prefix}, ${digits}`;
}

function formatTicketNumber(sequence) {
    return `Q-${String(sequence).padStart(3, "0")}`;
}

counterForm.addEventListener("submit", async event => {
    event.preventDefault();
    const formData = new FormData(counterForm);
    const id = formData.get("counterId");
    const name = formData.get("counterName");
    try {
        const response = await fetch("/api/counters", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ id, name })
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || "Gagal menyimpan loket");
        }
        counterForm.reset();
        showFeedback(`Loket ${id} berhasil ditambahkan.`);
        await loadStatus();
    } catch (error) {
        console.error(error);
        showFeedback(error.message, true);
    }
});

function showFeedback(message, isError = false) {
    if (!feedbackElement) {
        return;
    }
    if (!message) {
        feedbackElement.classList.add("hidden");
        return;
    }
    feedbackElement.textContent = message;
    feedbackElement.className = `feedback ${isError ? "error" : ""}`;
    feedbackElement.classList.remove("hidden");
}

window.addEventListener("load", () => {
    loadStatus();
    if (!voiceEnabled) {
        showFeedback("Browser Anda tidak mendukung Web Speech API. Fitur suara dinonaktifkan.", true);
    }
    refreshTimer = setInterval(loadStatus, 5000);
});

window.addEventListener("beforeunload", () => {
    if (refreshTimer) {
        clearInterval(refreshTimer);
    }
});
