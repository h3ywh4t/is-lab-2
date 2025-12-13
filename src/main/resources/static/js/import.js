let selectedFiles = [];
let activeImports = new Map();
let currentUserName = 'user';
let currentIsAdmin = false;

document.addEventListener('DOMContentLoaded', function () {
    const fileInput = document.getElementById('file-input');
    const selectFilesBtn = document.getElementById('select-files-btn');
    const uploadBtn = document.getElementById('upload-btn');
    const selectedFilesDiv = document.getElementById('selected-files');

    selectFilesBtn.addEventListener('click', () => {
        fileInput.click();
    });

    fileInput.addEventListener('change', (e) => {
        const newFiles = Array.from(e.target.files);

        for (const file of newFiles) {
            if (!selectedFiles.some(f => f.name === file.name && f.size === file.size)) {
                if (selectedFiles.length >= 5) {
                    alert('Maximum 5 files');
                    break;
                }
                if (file.name.toLowerCase().endsWith('.csv') || file.name.toLowerCase().endsWith('.json')) {
                    selectedFiles.push(file);
                } else {
                    alert('File ' + file.name + ' is not CSV or JSON');
                }
            }
        }

        fileInput.value = '';
        updateSelectedFilesDisplay();
        uploadBtn.disabled = selectedFiles.length === 0;
    });

    uploadBtn.addEventListener('click', async () => {
        if (selectedFiles.length === 0) return;

        const userName = prompt('Enter your user name:', 'user') || 'user';
        const isAdmin = confirm('Are you an admin?');

        currentUserName = userName;
        currentIsAdmin = isAdmin;

        await uploadMultipleFiles(selectedFiles, userName, isAdmin);

        selectedFiles = [];
        fileInput.value = '';
        updateSelectedFilesDisplay();
        uploadBtn.disabled = true;
    });

    function updateSelectedFilesDisplay() {
        if (selectedFiles.length === 0) {
            selectedFilesDiv.innerHTML = '<p>No files selected</p>';
            return;
        }

        let html = '<ul style="list-style: none; padding: 0;">';
        selectedFiles.forEach((file, index) => {
            html += `<li style="display: flex; justify-content: space-between; align-items: center; padding: 8px; margin: 5px 0; background: #2a2a2a; border-radius: 5px;">
                <span>${file.name} (${(file.size / 1024).toFixed(2)} KB)</span>
                <button class="btn btn-danger btn-small" onclick="removeFile(${index})" style="margin-left: 10px; padding: 4px 8px; font-size: 12px;">Remove</button>
            </li>`;
        });
        html += '</ul>';
        selectedFilesDiv.innerHTML = html;
    }

    window.removeFile = function (index) {
        selectedFiles.splice(index, 1);
        updateSelectedFilesDisplay();
        uploadBtn.disabled = selectedFiles.length === 0;
    };
});

async function uploadMultipleFiles(files, userName, isAdmin) {
    const formData = new FormData();
    for (const file of files) {
        formData.append('files', file);
    }
    formData.append('userName', userName);
    formData.append('isAdmin', isAdmin);

    try {
        const response = await fetch('/api/import/upload-multiple', {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Upload failed');
        }

        const result = await response.json();
        const imports = result.imports || [];

        for (const importInfo of imports) {
            const importId = importInfo.importId;
            const fileName = importInfo.fileName;

            if (importId) {
                createProgressElement(importId, fileName);
                startPolling(importId);
            }
        }

    } catch (error) {
        console.error('Upload error:', error);
        alert('Upload failed: ' + error.message);
    }
}

function createProgressElement(importId, fileName) {
    const container = document.getElementById('import-progress-container');
    if (document.getElementById(`progress-${importId}`)) {
        return;
    }

    const progressDiv = document.createElement('div');
    progressDiv.id = `progress-${importId}`;
    progressDiv.className = 'import-progress-item';
    progressDiv.innerHTML = `
        <div class="progress-header">
            <span class="progress-file-name">${fileName}</span>
            <span class="progress-status" id="status-${importId}">Starting...</span>
        </div>
        <div class="progress-bar-container">
            <div class="progress-bar" id="bar-${importId}" style="width: 0%">0%</div>
        </div>
        <div class="progress-details" id="details-${importId}">
            <span>0 / 0 records</span>
            <span>Successful: 0</span>
            <span>Failed: 0</span>
        </div>
    `;
    container.appendChild(progressDiv);
}

function updateProgress(progress) {
    const importId = progress.importId;
    let progressDiv = document.getElementById(`progress-${importId}`);

    if (!progressDiv) {
        createProgressElement(importId, progress.fileName || 'Unknown');
        progressDiv = document.getElementById(`progress-${importId}`);
    }

    if (!progressDiv) return;

    const bar = document.getElementById(`bar-${importId}`);
    const status = document.getElementById(`status-${importId}`);
    const details = document.getElementById(`details-${importId}`);

    if (bar) {
        const progressValue = progress.currentProgress || 0;
        bar.style.width = progressValue + '%';
        bar.textContent = progressValue + '%';
    }

    if (status) {
        const statusText = progress.status || 'IN_PROGRESS';
        status.textContent = statusText;
        status.className = 'progress-status status-' + statusText.toLowerCase().replace('_', '-');
    }

    if (details) {
        details.innerHTML = `
            <span>${progress.processedRecords || 0} / ${progress.totalRecords || 0} records</span>
            <span>Successful: ${progress.successfulRecords || 0}</span>
            <span>Failed: ${progress.failedRecords || 0}</span>
        `;
    }

    if (progress.status === 'COMPLETED' || progress.status === 'FAILED') {
        stopPolling(importId);

        if (window.currentView === 'table' && typeof window.loadTable === 'function') {
            window.loadTable();
        }

        if (progress.errorMessage) {
            const errorDiv = document.createElement('div');
            errorDiv.className = 'progress-error';
            errorDiv.textContent = 'Error: ' + progress.errorMessage;
            progressDiv.appendChild(errorDiv);
        }

        setTimeout(() => {
            if (progressDiv && progressDiv.parentNode) {
                progressDiv.parentNode.removeChild(progressDiv);
            }
            activeImports.delete(importId);
        }, 10000);
    }
}

const pollingIntervals = new Map();

function startPolling(importId) {
    if (pollingIntervals.has(importId)) {
        return;
    }

    const interval = setInterval(async () => {
        try {
            const response = await fetch(`/api/import/progress/${importId}`);
            if (response.ok) {
                const progress = await response.json();
                updateProgress(progress);

                if (progress.status === 'COMPLETED' || progress.status === 'FAILED') {
                    stopPolling(importId);
                }
            } else if (response.status === 404) {
                stopPolling(importId);
            }
        } catch (error) {
            console.error('Polling error:', error);
        }
    }, 500);

    pollingIntervals.set(importId, interval);
    activeImports.set(importId, {interval: interval});
}

function stopPolling(importId) {
    const importInfo = activeImports.get(importId);
    if (importInfo && importInfo.interval) {
        clearInterval(importInfo.interval);
    }
    pollingIntervals.delete(importId);
    activeImports.delete(importId);
}

window.updateImportProgress = updateProgress;

