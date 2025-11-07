const countersElement = document.getElementById("display-counters");
const queueElement = document.getElementById("display-queue");
const nextNumberElement = document.getElementById("display-next-number");
const lastCallNumberElement = document.getElementById("last-call-number");
const lastCallCounterElement = document.getElementById("last-call-counter");

let refreshTimer;
let lastDisplayedKey = null;

async function refreshDisplay() {
    try {
        const [countersResponse, queueResponse] = await Promise.all([
            fetch("/api/counters"),
            fetch("/api/queue/status")
        ]);

        if (!countersResponse.ok) {
            throw new Error("Gagal memuat data loket");
        }
        if (!queueResponse.ok) {
            throw new Error("Gagal memuat status antrean");
        }

        const counters = await countersResponse.json();
        const queueStatus = await queueResponse.json();

        renderCounters(counters);
        renderQueue(queueStatus.waitingQueue);
        updateNextNumber(queueStatus.nextTicketNumber);
        updateLastCall(counters);
    } catch (error) {
        console.error(error);
    }
}

function renderCounters(counters) {
    countersElement.innerHTML = "";
    counters.forEach(counter => {
        const card = document.createElement("div");
        card.className = "display-counter-card";
        card.innerHTML = `
            <h3>${counter.name}</h3>
            <p class="display-ticket">${counter.currentTicket ? counter.currentTicket.number : "-"}</p>
        `;
        countersElement.appendChild(card);
    });
}

function renderQueue(queue) {
    queueElement.innerHTML = "";
    if (!Array.isArray(queue) || queue.length === 0) {
        const item = document.createElement("li");
        item.textContent = "Tidak ada antrean";
        queueElement.appendChild(item);
        return;
    }
    queue.slice(0, 10).forEach(ticket => {
        const item = document.createElement("li");
        item.textContent = ticket.number;
        queueElement.appendChild(item);
    });
}

function updateNextNumber(sequence) {
    if (!Number.isInteger(sequence) || sequence < 1) {
        nextNumberElement.textContent = "-";
        return;
    }
    nextNumberElement.textContent = formatTicketNumber(sequence);
}

function updateLastCall(counters) {
    let latest = null;
    counters.forEach(counter => {
        if (!counter.currentTicket || !counter.lastCalledAt) {
            return;
        }
        const calledAt = new Date(counter.lastCalledAt);
        if (!latest || calledAt > latest.calledAt) {
            latest = {
                key: `${counter.currentTicket.id}:${counter.id}:${counter.lastCalledAt}`,
                number: counter.currentTicket.number,
                counterName: counter.name,
                calledAt
            };
        }
    });

    if (!latest) {
        lastDisplayedKey = null;
        lastCallNumberElement.textContent = "-";
        lastCallCounterElement.textContent = "-";
        return;
    }

    if (latest.key === lastDisplayedKey) {
        return;
    }

    lastDisplayedKey = latest.key;
    lastCallNumberElement.textContent = latest.number;
    lastCallCounterElement.textContent = latest.counterName;
}

function formatTicketNumber(sequence) {
    return `Q-${String(sequence).padStart(3, "0")}`;
}

window.addEventListener("load", () => {
    refreshDisplay();
    refreshTimer = setInterval(refreshDisplay, 4000);
});

window.addEventListener("beforeunload", () => {
    if (refreshTimer) {
        clearInterval(refreshTimer);
    }
});
