import http from 'k6/http';
import { check, sleep } from 'k6';
import { FormData } from 'https://jslib.k6.io/formdata/0.0.2/index.js';
import exec from 'k6/execution';

export const options = {
    stages: [
        { duration: '30s', target: 10 },
        { duration: '30s', target: 100 },
    ],
};

const binFileMap = {
    small: {
        jpg: open('./assets/small/small.jpg', 'b'),
        png: open('./assets/small/small.png', 'b'),
        avif: open('./assets/small/small.avif', 'b'),
        heic: open('./assets/small/small.heic', 'b'),
        gif: open('./assets/small/small.gif', 'b'),
        jxl: open('./assets/small/small.jxl', 'b'),
        webp: open('./assets/small/small.webp', 'b')
    },
    medium: {
        jpg: open('./assets/medium/medium.jpg', 'b'),
        png: open('./assets/medium/medium.png', 'b'),
        avif: open('./assets/medium/medium.avif', 'b'),
        heic: open('./assets/medium/medium.heic', 'b'),
        gif: open('./assets/medium/medium.gif', 'b'),
        jxl: open('./assets/medium/medium.jxl', 'b'),
        webp: open('./assets/medium/medium.webp', 'b')
    },
    large: {
        jpg: open('./assets/large/large.jpg', 'b'),
        png: open('./assets/large/large.png', 'b'),
        avif: open('./assets/large/large.avif', 'b'),
        heic: open('./assets/large/large.heic', 'b'),
        gif: open('./assets/large/large.gif', 'b'),
        jxl: open('./assets/large/large.jxl', 'b'),
        webp: open('./assets/large/large.webp', 'b')
    },
};

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
    const currentPhase = exec.instance.currentTestRunDuration < 30000 ? 'warmup' : 'measurement';

    const size = __ENV.IMAGE_SIZE || 'small';
    const format = __ENV.IMAGE_FORMAT || 'jpg';

    const img = binFileMap[size][format];
    const fd = new FormData();
    fd.append('file', {
        data: new Uint8Array(img).buffer,
        filename: 'medium.jpg',
        content_type: 'image/jpeg',
    });
    fd.append('metadata', {
        data: JSON.stringify(metadata),
        content_type: 'application/json'
    });

    const res = http.post('http://localhost:8080/assets/test-upload-preprocessing', fd.body(), {
        headers: { 'Content-Type': 'multipart/form-data; boundary=' + fd.boundary },
    });
    check(res, {
        'is status 201': (r) => r.status === 201,
    });
}
