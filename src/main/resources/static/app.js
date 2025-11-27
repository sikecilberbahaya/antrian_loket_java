const counterListElement = document.getElementById("counter-list");
const queueItemsElement = document.getElementById("queue-items");
const nextNumberElement = document.getElementById("next-number");
const queueLengthElement = document.getElementById("queue-length");
const feedbackElement = document.getElementById("feedback");
const counterForm = document.getElementById("counter-form");
const resetButton = document.getElementById("reset-queue");

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
        const activeTickets = Array.isArray(counter.activeTickets) ? counter.activeTickets : [];
        const primaryTicket = activeTickets.length > 0 ? activeTickets[0] : null;
        const selectMarkup = activeTickets.length === 0
            ? `<p class="secondary-text">Tidak ada nomor aktif.</p>`
            : `
                <label class="active-select-label" for="active-${counter.id}">Pilih Nomor Aktif</label>
                <select id="active-${counter.id}" data-counter="${counter.id}" class="active-select">
                    ${activeTickets.map(ticket => `<option value="${ticket.id}">${ticket.number}</option>`).join("")}
                </select>
            `;
        const card = document.createElement("article");
        card.className = "counter-card";
        card.innerHTML = `
            <div class="counter-header">
                <h3>${counter.name}</h3>
                <span class="secondary-text">ID: ${counter.id}</span>
            </div>
            <div>
                <p class="secondary-text">Sedang Dilayani</p>
                <p class="current-ticket">${primaryTicket ? primaryTicket.number : "-"}</p>
                ${selectMarkup}
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
    let selectedTicket = null;

    if (action === "call") {
        endpoint = `/api/counters/${counterId}/call-next`;
    } else if (action === "recall") {
        selectedTicket = getSelectedTicket(counterId);
        if (!selectedTicket) {
            showFeedback(`Loket ${counterId} tidak memiliki nomor aktif untuk dipanggil ulang.`, true);
            return;
        }
        endpoint = `/api/counters/${counterId}/recall?ticketId=${encodeURIComponent(selectedTicket.id)}`;
    } else if (action === "complete") {
        selectedTicket = getSelectedTicket(counterId);
        if (!selectedTicket) {
            showFeedback(`Loket ${counterId} tidak memiliki nomor aktif untuk diselesaikan.`, true);
            return;
        }
        endpoint = `/api/counters/${counterId}/complete?ticketId=${encodeURIComponent(selectedTicket.id)}`;
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
            const label = selectedTicket ? selectedTicket.label : "saat ini";
            showFeedback(`Loket ${counterId} selesai melayani nomor ${label}.`);
        } else {
            const ticket = await response.json();
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

function getSelectedTicket(counterId) {
    const selector = document.querySelector(`select[data-counter="${counterId}"]`);
    if (!selector || selector.options.length === 0) {
        return null;
    }
    const option = selector.options[selector.selectedIndex];
    return {
        id: option.value,
        label: option.textContent
    };
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

if (resetButton) {
    resetButton.addEventListener("click", async () => {
        if (!confirm("Reset manual akan mengosongkan antrean hari ini. Lanjutkan?")) {
            return;
        }
        try {
            const response = await fetch("/api/queue/reset", { method: "POST" });
            if (!response.ok) {
                let message = "Gagal mereset antrean";
                try {
                    const error = await response.json();
                    message = error.error || message;
                } catch (ignore) {
                    // gunakan pesan default
                }
                throw new Error(message);
            }
            showFeedback("Antrean berhasil direset untuk hari ini.");
            await loadStatus();
        } catch (error) {
            console.error(error);
            showFeedback(error.message, true);
        }
    });
}

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
    refreshTimer = setInterval(loadStatus, 5000);
});

window.addEventListener("beforeunload", () => {
    if (refreshTimer) {
        clearInterval(refreshTimer);
    }
});
