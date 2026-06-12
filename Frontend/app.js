let localItems = [];

document.addEventListener('DOMContentLoaded', () => {
    fetchPrices(); 
    document.getElementById('add-item-form').addEventListener('submit', handleFormSubmit);
});

const API_URL = 'https://38zjrp184k.execute-api.ap-south-1.amazonaws.com/prod/item';

async function fetchPrices() {
    const container = document.getElementById('tracked-items-container');
    container.innerHTML = '<p>Loading live data from AWS Cloud...</p>';

    try {
        const response = await fetch(API_URL);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const rawData = await response.json();
        
        // AWS Lambda wraps the data in a stringified 'body'. We must parse it.
        let itemsArray = [];
        if (rawData.body) {
            itemsArray = JSON.parse(rawData.body);
        } else {
            itemsArray = rawData; // Fallback in case it is already an array
        }


        container.innerHTML = '';

        if (itemsArray.length === 0) {
            container.innerHTML = '<p>No items currently tracked. Add one above!</p>';
            return;
        }

        // Loop through the data and build the HTML cards
        itemsArray.forEach(item => {
            const card = document.createElement('div');
            card.className = 'item-card';
    
            const priceColor = item.currentPrice > 0 && item.currentPrice <= item.targetPrice ? 'green' : 'red';

            card.innerHTML = `
                <h3>${item.itemName}</h3>
                <p>Target Price: <strong>₹${item.targetPrice}</strong></p>
                <p style="color: ${priceColor}; background-color: white; padding: 0.3rem; border-radius:3px; font-weight: bold;">Current Price: ₹${item.currentPrice}</p>
                <p><small>Last Updated: ${new Date(item.timestamp).toLocaleString()}</small></p>
            `;
            container.appendChild(card);
        });

    } catch (error) {
        console.error("Fetch API Error:", error);
        container.innerHTML = '<p style="color: red;">Failed to fetch from API.</p>';
    }
}


document.addEventListener('DOMContentLoaded', fetchPrices);

async function handleFormSubmit(event) {
    event.preventDefault(); 
    const payload = {
        itemName: document.getElementById('itemName').value,
        targetPrice: parseFloat(document.getElementById('targetPrice').value),
        amazonUrl: document.getElementById('amazonUrl').value,
        myntraUrl: document.getElementById('myntraUrl').value,
        flipkartUrl: document.getElementById('flipkartUrl').value
    };

    try {
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (response.ok) {
            alert('Success! Target saved!');
            document.getElementById('add-item-form').reset();
            fetchPrices(); 
        } else {
            alert('Something went wrong with the cloud upload.');
        }
    } catch (error) {
        console.error("Fetch error:", error);
        alert('Network error. Check your console.');
    }
}


// ==============================================================================
// UI RENDERER (Updated Layout)
// ==============================================================================
function renderCards(items) {
    const grid = document.getElementById('tracked-items-container');
    grid.innerHTML = ''; 
    
    if (!items || items.length === 0) {
        grid.innerHTML = '<p>No items found. Add a target above or run the Java scraper!</p>';
        return;
    }
    
    items.forEach(item => {
        const card = document.createElement('div');
        card.className = 'card';
        
        let statusColor = "orange";
        let statusText = "Waiting for Java scan...";
        
        if (item.currentPrice && item.currentPrice > 0) {
            if (item.currentPrice <= item.targetPrice) {
                statusColor = "green";
                statusText = "Target Met!";
            } else {
                statusColor = "yellow";
                statusText = "Tracking...";
            }
        }
        
        let linksHtml = '<div class="links" style="margin-bottom: 15px;">';
        if (item.amazonUrl) linksHtml += `<a href="${item.amazonUrl}" target="_blank" class="btn btn-small">Amazon</a>`;
        if (item.myntraUrl) linksHtml += `<a href="${item.myntraUrl}" target="_blank" class="btn btn-small">Myntra</a>`;
        if (item.flipkartUrl) linksHtml += `<a href="${item.flipkartUrl}" target="_blank" class="btn btn-small">Flipkart</a>`;
        linksHtml += '</div>';

        card.innerHTML = `
            <h3 style="margin-top:0;">${item.itemName}</h3>
            ${linksHtml}
            <hr style="border: 0; height: 1px; background: #eee; margin: 10px 0;">
            
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
                <div>
                    <span style="font-size: 0.9em; color: white;">Target Price</span><br>
                    <strong>₹${item.targetPrice}</strong>
                </div>
                <div style="text-align: right;">
                    <span style="font-size: 0.9em; color: white;">Current Lowest</span><br>
                    <strong style="color: ${statusColor}; font-size: 1.2em;">₹${item.currentPrice || '0.0'}</strong>
                </div>
            </div>
            
            <div style="font-size: 0.85em; color: ${statusColor}; font-weight: bold; margin-bottom: 15px; text-align: center;">
                ${statusText}
            </div>
            
            <div style="background: #2a5f95ff; padding: 10px; border-radius: 5px; font-size: 0.9em;">
                <label style="display: block; margin-bottom: 5px; cursor: pointer;">
                    <input type="checkbox" checked> Send notification when target is met
                </label>
                <label style="display: block; cursor: pointer;">
                    <input type="checkbox"> Send 12h schedule summaries
                </label>
            </div>
        `;
        grid.appendChild(card);
    });
}