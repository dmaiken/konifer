import http from 'k6/http';
import { check, sleep } from 'k6';
import { FormData } from 'https://jslib.k6.io/formdata/0.0.2/index.js';

export const options = {
    stages: [
        { duration: '30s', target: 10 },
        { duration: '30s', target: 100 },
    ],
};

const img = open('./assets/zion.jpg', 'b');
const metadata = {
    alt: "Zion National Park",
    tags: [
        "hiking",
        "Zion",
        "vacation"
    ],
    labels: {
        vacationYear: "2025"
    }
}

export default function () {
    const fd = new FormData();
    fd.append('file', {
        data: new Uint8Array(img).buffer,
        filename: 'zion.jpg',
        content_type: 'image/jpeg',
    });
    fd.append('metadata', {
        data: JSON.stringify(metadata),
        content_type: 'application/json'
    });

    const res = http.post('http://localhost:8080/assets/test-upload-eager-variants', fd.body(), {
        headers: { 'Content-Type': 'multipart/form-data; boundary=' + fd.boundary },
    });
    check(res, {
        'is status 201': (r) => r.status === 201,
    });
}
