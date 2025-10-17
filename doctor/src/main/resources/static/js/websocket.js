let stompClient = null;

function connectWebSocket() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function (frame) {
        console.log('WebSocket соединение установлено:', frame);

        stompClient.subscribe('/topic/visits', function (message) {
            console.log('Новое обновление визитов:', message.body);
            updateVisits();
        });
    }, function (error) {
        console.error('Ошибка WebSocket:', error);
        setTimeout(connectWebSocket, 5000);
    });
}

async function updateVisits() {
    try {
        const res = await fetch('/api/visits?doctor=' + encodeURIComponent(DOCTOR_NAME));
        if (!res.ok) {
            console.error('Failed to fetch visits', res.status);
            return;
        }
        const visits = await res.json();
        const tbody = document.getElementById('visits-body');
        tbody.innerHTML = '';
        for (const v of visits) {
            tbody.insertAdjacentHTML('beforeend', `
            <tr class="border-b hover:bg-gray-50">
                <td class="py-2 px-4">${escapeHtml(v.patientRef || v['patientRef'] || '')}</td>
                <td class="py-2 px-4">${escapeHtml(v.visitDate || '')}</td>
                <td class="py-2 px-4">${escapeHtml(v.reason || '')}</td>
                <td class="py-2 px-4 font-semibold">${escapeHtml(v.status || '')}</td>
            </tr>
        `);
        }
    } catch (ex) {
        console.error('updateVisits error', ex);
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

document.addEventListener('DOMContentLoaded', function () {
    connectWebSocket();
    updateVisits();
});
