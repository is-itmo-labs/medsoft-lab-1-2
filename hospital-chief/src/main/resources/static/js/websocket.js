// websocket.js

let stompClient = null;

function connectWebSocket() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function (frame) {
        console.log('WebSocket соединение установлено:', frame);

        stompClient.subscribe('/topic/patients', function (message) {
            console.log('Новое обновление пациентов:', message.body);
            updatePatientTable();
        });
    }, function (error) {
        console.error('Ошибка WebSocket:', error);
        setTimeout(connectWebSocket, 5000); // попытка переподключения
    });
}

async function updatePatientTable() {
    const res = await fetch('/api/patients');
    const patients = await res.json();
    const tbody = document.getElementById('patients-body');
    tbody.innerHTML = '';
    for (const p of patients) {
        tbody.insertAdjacentHTML('beforeend', `
            <tr>
                <td class="p-2 border">${p.id}</td>
                <td class="p-2 border">${p.lastName}</td>
                <td class="p-2 border">${p.firstName}</td>
                <td class="p-2 border">${p.birthDate}</td>
            </tr>
        `);
    }
}

document.addEventListener('DOMContentLoaded', function () {
    connectWebSocket();
});
