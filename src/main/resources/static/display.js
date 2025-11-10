const countersElement = document.getElementById("display-counters");
const queueElement = document.getElementById("display-queue");
const nextNumberElement = document.getElementById("display-next-number");
const lastCallNumberElement = document.getElementById("last-call-number");
const lastCallCounterElement = document.getElementById("last-call-counter");
const audioBannerElement = document.getElementById("audio-banner");
const enableAudioButton = document.getElementById("enable-audio");
const audioStatusElement = document.getElementById("audio-status");

let refreshTimer;
let lastDisplayedKey = null;
const speechSupported = "speechSynthesis" in window;
let audioEnabled = false;

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
    announceTicket(latest.number, latest.counterName);
}

function formatTicketNumber(sequence) {
    return `Q-${String(sequence).padStart(3, "0")}`;
}

function announceTicket(ticketNumber, counterName) {
    if (!speechSupported || !audioEnabled || !ticketNumber || !counterName) {
        return;
    }
    const sentence = `Nomor antrean ${spellTicket(ticketNumber)} menuju ${counterName}`;
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
    const digits = parts[1]
        .split("")
        .map(spellDigit)
        .join(" ");
    return `${prefix}, ${digits}`;
}

function spellDigit(digit) {
    switch (digit) {
        case "0":
            return "nol";
        case "1":
            return "satu";
        case "2":
            return "dua";
        case "3":
            return "tiga";
        case "4":
            return "empat";
        case "5":
            return "lima";
        case "6":
            return "enam";
        case "7":
            return "tujuh";
        case "8":
            return "delapan";
        case "9":
            return "sembilan";
        default:
            return digit;
    }
}

window.addEventListener("load", () => {
    setupAudioControls();
    refreshDisplay();
    refreshTimer = setInterval(refreshDisplay, 4000);
});

window.addEventListener("beforeunload", () => {
    if (refreshTimer) {
        clearInterval(refreshTimer);
    }
});

function setupAudioControls() {
    if (!speechSupported) {
        if (audioBannerElement) {
            audioBannerElement.classList.add("hidden");
        }
        audioEnabled = false;
        return;
    }
    if (!enableAudioButton || !audioStatusElement) {
        audioEnabled = true;
        return;
    }

    enableAudioButton.addEventListener("click", () => {
        try {
            window.speechSynthesis.resume();
            const confirmation = new SpeechSynthesisUtterance("Suara pemanggilan aktif.");
            confirmation.lang = "id-ID";
            window.speechSynthesis.cancel();
            window.speechSynthesis.speak(confirmation);
            audioEnabled = true;
            enableAudioButton.disabled = true;
            enableAudioButton.textContent = "Suara Aktif";
            audioStatusElement.textContent = "Suara pemanggilan aktif. Pastikan volume perangkat hidup.";
        } catch (error) {
            console.error("Gagal mengaktifkan suara", error);
            audioStatusElement.textContent = "Gagal mengaktifkan suara. Periksa pengaturan browser.";
        }
    });
}
