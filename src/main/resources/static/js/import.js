let selectedFiles = [];
let activeImports = new Map();
let currentUserName = 'user';
let currentIsAdmin = false;

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

