const startButton = document.getElementById('start');
const submitButton = document.getElementById('submit');
const topicSelect = document.getElementById('topic');
const difficultySelect = document.getElementById('difficulty');
const statusEl = document.getElementById('status');
const promptEl = document.getElementById('prompt');
const questionEl = document.getElementById('question');
const timerEl = document.getElementById('timer');
const answerInput = document.getElementById('answer');
const resultEl = document.getElementById('result');
const heatmapCanvas = document.getElementById('heatmap');

const postGameActions = document.getElementById('post-game-actions');
const btnSaveScore = document.getElementById('btn-save-score');
const btnRestart = document.getElementById('btn-restart');
const statsContainer = document.getElementById('stats-container');
const btnClearStats = document.getElementById('btn-clear-stats');
const btnAiSummary = document.getElementById('btn-ai-summary');
const aiSummaryOutput = document.getElementById('ai-summary-output');

  const CGI_TASK = '/cgi-bin/QuestionSelector';
  const CGI_DATA = '/cgi-bin/DataManager';
  const CGI_AI = '/cgi-bin/AISummary';
  const memoryDisplayMs = 3000;let currentTask = null;
let startTime = null;
let timerHandle = null;
let categorizeState = null;
let sequenceState = null;
let lastGameResult = null; // store before saving

startButton.addEventListener('click', startTraining);
submitButton.addEventListener('click', submitTraining);

window.addEventListener('load', () => {
  setupTabs();
  renderStats();
});

btnRestart.addEventListener('click', startTraining);
btnSaveScore.addEventListener('click', saveScoreToLocal);
btnClearStats.addEventListener('click', () => {
    if(confirm('确定清空所有本地练习记录吗？')) {
        localStorage.removeItem('neuroflex_stats');
        renderStats();
    }
});
btnAiSummary.addEventListener('click', () => {
    const data = getLocalStats();
    if(data.length === 0) {
        aiSummaryOutput.style.display = 'block';
        aiSummaryOutput.textContent = '暂无数据可以分析。请先进行一些训练！';
        return;
    }
    aiSummaryOutput.style.display = 'block';
    aiSummaryOutput.textContent = 'AI 分析中...请稍候...';

    fetch(CGI_AI, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
    .then(r => r.json())
    .then(res => {
        if(res.error) {
            aiSummaryOutput.textContent = 'AI 分析失败：' + res.error;
        } else {
            aiSummaryOutput.textContent = 'AI 总结与分析：\n\n' + res.summary;
        }
    })
    .catch(e => {
        aiSummaryOutput.textContent = 'AI 分析发生网络错误：' + e.message;
    });
});function setupTabs() {
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      // Deactivate all
      document.querySelectorAll('.tab-btn').forEach(b => {
          b.classList.remove('active');
          b.style.background = '#21262d';
          b.style.color = '#c9d1d9';
      });
      document.querySelectorAll('.tab-pane').forEach(p => p.style.display = 'none');
      // Activate current
      btn.classList.add('active');
      btn.style.background = '#238636';
      btn.style.color = '#fff';
      document.getElementById(btn.dataset.target).style.display = 'block';
      if(btn.dataset.target === 'tab-stats'){
          renderStats();
      }
    });
  });
}

const RULES = {
  schulte: "规则：请按照 1 到 N 的顺序，尽可能快地点击屏幕上的数字方块。\n\n示例：如果包含9个格子，依次点击 1, 2, 3... 直到 9，每次点对都会变绿。",
  stroop: "规则：无视文字本身的意思，请选择文字【显示的颜色】。\n\n示例：如果屏幕上写着“红”字，但用蓝色的墨水渲染，你需要点击下方的【蓝】按钮。",
  sequence: "规则：屏幕上会先展现一组物品顺序。接着它们会被打乱并可以交互。请通过【点击选择再点击目标位交换】将它们恢复成原始顺序。\n\n示例：仔细记住苹果、汽车，然后将其重新排列。",
  mirror: "规则：左侧区域展示了随机的点阵高亮图案。请在右侧的交互网格中，点击单元格，绘制出其严格的“镜像（左右对称翻转）”图案！\n\n示例：左侧点在左数第1列，右侧镜像点应当在右数第1列（即左数第4列）。",
  categorize: "规则：系统会给出各种类型的物品（食品/用品/交通工具等），请针对每个物品点击对应的分类标签。\n\n提示：点错会立刻红框警告提示！请尽量做到全优",
  memory_story: "规则：屏幕上会出现一组带图标的情景物品，你有 3 秒钟的时间努力记住它们分别是什么。它们消失后，请从突然出现的大量混淆项中，重新把刚才出现过的物品全部精准挑拾出来！"
};

let modalOverlay = null;
function showRuleModal(task, onConfirm) {
  if (modalOverlay) document.body.removeChild(modalOverlay);
  modalOverlay = document.createElement('div');
  modalOverlay.style.position = 'fixed';
  modalOverlay.style.top = '0'; modalOverlay.style.left = '0';
  modalOverlay.style.width = '100vw'; modalOverlay.style.height = '100vh';
  modalOverlay.style.backgroundColor = 'rgba(0,0,0,0.6)';
  modalOverlay.style.display = 'flex';
  modalOverlay.style.alignItems = 'center';
  modalOverlay.style.justifyContent = 'center';
  modalOverlay.style.zIndex = '9999';
  
  const box = document.createElement('div');
  box.style.backgroundColor = '#161b22';
  box.style.border = '1px solid #30363d';
  box.style.color = '#c9d1d9';
  box.style.padding = '30px';
  box.style.borderRadius = '12px';
  box.style.maxWidth = '500px';
  box.style.boxShadow = '0 10px 25px rgba(0,0,0,0.5)';

  const title = document.createElement('h2');
  title.textContent = (task.prompt.split('-')[0] || "游戏规则");
  title.style.marginTop = '0';
  title.style.color = '#c9d1d9';  const desc = document.createElement('p');
  desc.innerText = RULES[task.type] || "请根据提示完成任务。";
  desc.style.lineHeight = '1.6';
  desc.style.color = '#555';
  desc.style.whiteSpace = 'pre-wrap';
  
  const btn = document.createElement('button');
  btn.textContent = "我已了解，开始游戏";
  btn.style.width = '100%';
  btn.style.marginTop = '20px';
  btn.style.padding = '12px';
  btn.style.fontSize = '16px';
  btn.style.backgroundColor = '#238636';
  btn.style.color = '#fff';
  btn.style.border = '1px solid rgba(240, 246, 252, 0.1)';
  btn.style.borderRadius = '6px';
  btn.style.cursor = 'pointer';
  
  btn.onclick = () => {
    document.body.removeChild(modalOverlay);
    modalOverlay = null;
    onConfirm();
  };
  
  box.appendChild(title);
  box.appendChild(desc);
  box.appendChild(btn);
  modalOverlay.appendChild(box);
  document.body.appendChild(modalOverlay);
}

async function startTraining() {
  resetUI();
  const topic = topicSelect.value;
  const difficulty = difficultySelect.value;
  statusEl.textContent = '正在获取题目...';
  try {
    const payload = { topic, difficulty };
    const response = await fetch(CGI_TASK, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const text = await response.text();
    currentTask = safeJsonParse(text);
    if (currentTask.error) {
      statusEl.textContent = `题目获取失败：${currentTask.error}`;
      return;
    }
    
    showRuleModal(currentTask, () => {
        statusEl.textContent = '规则明确，祝你好运！';
        showTask(currentTask);
        startTime = performance.now();
        startTimer();
    });
  } catch (error) {
    statusEl.textContent = `题目获取失败：${error.message}`;
  }
}

function showTask(task) {
  categorizeState = null;
  sequenceState = null;
  
  if (task.type === 'schulte') {
    promptEl.textContent = task.prompt;
    setupSchulte(task);
    answerInput.style.display = 'none';
    statusEl.textContent = `按照 1 到 ${task.size * task.size} 的顺序点击方格`;
  } else if (task.type === 'stroop') {
    promptEl.textContent = task.prompt;
    setupStroop(task);
    answerInput.style.display = 'none';
    statusEl.textContent = '选择文字的颜色';
  } else if (task.type === 'sequence') {
    promptEl.textContent = task.prompt;
    setupSequence(task);
    answerInput.style.display = 'block';
    answerInput.value = '';
    answerInput.placeholder = '输入你记住的顺序';
    statusEl.textContent = '观察序列后排序';
  } else if (task.type === 'mirror') {
    promptEl.textContent = task.prompt;
    setupMirror(task);
    answerInput.style.display = 'none';
    statusEl.textContent = '在右侧镜像绘制左侧的图形';
  } else if (task.type === 'categorize') {
    promptEl.textContent = task.prompt;
    setupCategorize(task);
    answerInput.style.display = 'none';
    statusEl.textContent = task.rule;
  } else if (task.type === 'memory_story') {
    promptEl.textContent = task.prompt;
    setupMemoryStory(task);
    answerInput.style.display = 'none';
    statusEl.textContent = '记住提示的情景物品';
  } else {
    promptEl.textContent = '未知题型';
    questionEl.innerHTML = '';
  }
}

async function submitTraining() {
  if (!currentTask || !startTime) {
    resultEl.textContent = '请先开始训练。';
    return;
  }
  if (timerHandle) clearInterval(timerHandle);
  
  const endTime = performance.now();
  const elapsed = (endTime - startTime) / 1000;
  const answer = answerInput.value.trim();
  const accuracy = calculateAccuracy(currentTask, answer);
  if (accuracy === null) {
    resultEl.textContent = `游戏结束 | 耗时：${elapsed.toFixed(1)}s`;
  } else {
    resultEl.textContent = `准确率：${Math.round(accuracy * 100)}% | 耗时：${elapsed.toFixed(1)}s`;
  }

  // Pre-prepare score for saving but wait for user decision
  lastGameResult = {
    type: currentTask.type,
    difficulty: currentTask.difficulty,
    accuracy: accuracy || 0,
    elapsedSeconds: elapsed,
    createdAt: new Date().toISOString()
  };
  
  postGameActions.style.display = 'flex';
  statusEl.textContent = '练习已完成。';
  submitButton.style.display = 'none';
}

function saveScoreToLocal() {
    if(!lastGameResult) return;
    const stats = getLocalStats();
    stats.push(lastGameResult);
    localStorage.setItem('neuroflex_stats', JSON.stringify(stats));
    btnSaveScore.textContent = "已保存至本地";
    btnSaveScore.style.background = "#6c757d";
    btnSaveScore.disabled = true;
    
    // Optionally also upload to Java API Manager if needed
    fetch(CGI_DATA, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(lastGameResult)
    }).catch(e => console.log('Data manager upload err:', e));
}

function getLocalStats() {
    try {
        return JSON.parse(localStorage.getItem('neuroflex_stats')) || [];
    } catch(e) {
        return [];
    }
}

function renderStats() {
    aiSummaryOutput.style.display = 'none';
    const data = getLocalStats();
    if (data.length === 0) {
        statsContainer.innerHTML = '<p style="color:#888;">暂无数据，快去训练大厅体验两把吧！</p>';
        return;
    }
    
    // Group by topic and difficulty
    const grouped = {};
    data.forEach(d => {
        if(!grouped[d.type]) grouped[d.type] = {1:[], 2:[], 3:[]};
        grouped[d.type][d.difficulty].push(d);
    });
    
    const topicNames = {
        schulte: "舒尔特方格",
        stroop: "Stroop色词",
        sequence: "序列记忆",
        mirror: "镜像协调",
        categorize: "规则分类",
        memory_story: "情景记忆"
    };
    const diffNames = {1:"简单", 2:"中等", 3:"困难"};
    
    let html = '';
    for(const [type, diffs] of Object.entries(grouped)) {
        let hasData = false;
        let diffHtml = '';
        for(const [diff, records] of Object.entries(diffs)) {
            if(records.length === 0) continue;
            hasData = true;
            const avgAcc = records.reduce((s, r)=>s+r.accuracy, 0)/records.length;
            const avgTime = records.reduce((s, r)=>s+r.elapsedSeconds, 0)/records.length;
            diffHtml += `
                <div style="background:#21262d; padding:10px; margin-top:10px; border-radius:8px; display:flex; justify-content:space-between; align-items:center; border: 1px solid #30363d;">
                    <div><strong>${diffNames[diff]}模式</strong> <span style="color:#8b949e; font-size:14px; margin-left:10px;">共玩了 ${records.length} 次</span></div>
                    <div style="text-align:right;">
                        <div style="color:#3fb950; font-weight:bold;">平均准确率: ${Math.round(avgAcc*100)}%</div>
                        <div style="color:#58a6ff; font-size:14px;">平均耗时: ${avgTime.toFixed(1)}s</div>
                    </div>
                </div>
            `;
        }
        if(hasData) {
            html += `
            <div style="border:1px solid #30363d; background: #0d1117; border-radius:10px; padding:15px; margin-bottom:15px;">
                <h3 style="margin-top:0; color:#c9d1d9;">${topicNames[type] || type}</h3>
                ${diffHtml}
            </div>
            `;
        }
    }
    statsContainer.innerHTML = html;
}

function calculateAccuracy(task, answer) {
  if (task.type === 'schulte') {
    return window.schulteCompleted ? 1.0 : 0.0;
  }
  if (task.type === 'stroop') {
    return window.stroopCorrect / Math.max(1, task.trials.length);
  }
  if (task.type === 'sequence' && sequenceState) {
    const expected = sequenceState.originalOrder;
    const current = sequenceState.currentOrder;
    if (!expected.length) {
      return null;
    }
    const correct = expected.filter((name, index) => current[index] === name).length;
    return correct / expected.length;
  }
  if (task.type === 'mirror') {
    return window.mirrorCorrect ? 1.0 : 0.0;
  }
  if (task.type === 'memory_story') {
     return window.memoryStorySelections === task.items.length ? 1.0 : 0.0;
  }
  if (task.type === 'categorize' && categorizeState) {
    const items = categorizeState.items;
    if (!items.length) {
      return null;
    }
    const correct = items.filter((item) => categorizeState.selections[item.name] === item.category).length;
    return correct / items.length;
  }
  return null;
}

function startTimer() {
  if (timerHandle) {
    clearInterval(timerHandle);
  }
  timerHandle = setInterval(() => {
    if (!startTime) {
      return;
    }
    const elapsed = (performance.now() - startTime) / 1000;
    timerEl.textContent = `计时：${elapsed.toFixed(1)}s`;
  }, 100);
}

function resetUI() {
  resultEl.textContent = '';
  timerEl.textContent = '计时：0.0s';
  questionEl.innerHTML = '';
  postGameActions.style.display = 'none';
  submitButton.style.display = 'inline-block';
  btnSaveScore.textContent = '保存并上传成绩';
  btnSaveScore.style.background = '#28a745';
  btnSaveScore.disabled = false;
}



function renderItems(items = [], fields = ['name']) {
  if (!items || items.length === 0) {
    return '<p class="muted">暂无题目数据</p>';
  }
  return `
    <ul class="item-list">
      ${items
        .map((item) => {
          const parts = fields
            .map((field) => (item && item[field] !== undefined ? `${item[field]}` : ''))
            .filter(Boolean)
            .join(' · ');
          return `<li>${parts}</li>`;
        })
        .join('')}
    </ul>
  `;
}

function setupCategorize(task) {
  const items = Array.isArray(task.items) ? task.items : [];
  if (!items.length) {
    questionEl.innerHTML = '<p class="muted">暂无题目数据</p>';
    return;
  }
  const categories = [...new Set(items.map((item) => item.category).filter(Boolean))];
  categorizeState = {
    items,
    selections: {}
  };
  questionEl.innerHTML = `
    <div class="categorize-grid">
      ${items
        .map(
          (item) => `
        <div class="categorize-card" data-item="${item.name}">
          <div class="categorize-title">${item.name} · ¥${item.price}</div>
          <div class="category-options">
            ${categories
              .map((cat) => `<button class="category-btn" data-item="${item.name}" data-category="${cat}">${cat}</button>`)
              .join('')}
          </div>
          <div class="category-selected" id="selected-${item.name}">未选择</div>
        </div>
      `
        )
        .join('')}
    </div>
  `;

  questionEl.querySelectorAll('.category-btn').forEach((button) => {
    button.addEventListener('click', () => {
      const itemName = button.dataset.item;
      const category = button.dataset.category;
      
      const itemData = items.find(i => i.name === itemName);
      if (itemData && itemData.category !== category) {
          // Error feedback
          const card = questionEl.querySelector(`.categorize-card[data-item="${CSS.escape(itemName)}"]`);
          const originalBg = card.style.backgroundColor;
          card.style.backgroundColor = '#490202';
          card.style.border = '2px solid #f85149';
          setTimeout(() => {
              card.style.backgroundColor = originalBg;
              card.style.border = '';
          }, 600);
          return; // optionally prevent selection if wrong, or just register it. We'll let them select but flash red.
      }
      
      categorizeState.selections[itemName] = category;
      const selected = questionEl.querySelector(`#selected-${CSS.escape(itemName)}`);
      if (selected) {
        selected.textContent = `已选：${category}`;
        selected.style.color = '#3fb950';
      }
      questionEl.querySelectorAll(`.category-btn[data-item="${CSS.escape(itemName)}"]`).forEach((btn) => {
        btn.classList.toggle('active', btn.dataset.category === category);
      });
      
      // Flash green for correct
      const card = questionEl.querySelector(`.categorize-card[data-item="${CSS.escape(itemName)}"]`);
      const originalBg = card.style.backgroundColor;
      card.style.backgroundColor = '#053814';
      card.style.border = '2px solid #2ea043';
      setTimeout(() => {
          card.style.backgroundColor = originalBg;
          card.style.border = '';
      }, 400);

    });
  });
}

function setupSequence(task) {
  const items = Array.isArray(task.sequenceItems) ? task.sequenceItems : [];
  if (!items.length) {
    questionEl.innerHTML = '<p class="muted">暂无题目数据</p>';
    return;
  }
  const names = items.map((item) => item.name);
  sequenceState = {
    originalOrder: [...names],
    currentOrder: [...names]
  };
  
  // Show original order first
  questionEl.innerHTML = `
    <p class="muted" style="color: #58a6ff;">请记住以下物品的顺序（3秒后打乱）：</p>
    <div class="sequence-list" style="display:flex; gap:10px; flex-wrap:wrap; justify-content:center; padding: 20px;">
      ${sequenceState.originalOrder.map((name) => `<div style="padding:15px; font-size:18px; border:2px solid #2ea043; background:#053814; color:#c9d1d9; border-radius:8px;">${name}</div>`).join('')}
    </div>
  `;

  setTimeout(() => {
    // Scramble the order for the user to solve
    sequenceState.currentOrder.sort(() => Math.random() - 0.5);

    questionEl.innerHTML = `
      <p class="muted">记忆时间结束！请点击选中一个卡片，再点击另一个即可互换位置恢复原序</p>
      <div class="sequence-list" id="sequence-list" style="display:flex; gap:10px; flex-wrap:wrap; justify-content:center; padding: 20px;">
        ${sequenceState.currentOrder.map((name, index) => `<button class="seq-btn" data-index="${index}" style="padding:15px; font-size:18px; cursor:pointer; border:2px solid #30363d; background:#21262d; color:#c9d1d9; border-radius:8px;">${name}</button>`).join('')}
      </div>
    `;
    enableClickSwap();
  }, 3000);
}

function enableClickSwap() {
  const list = questionEl.querySelector('#sequence-list');
  if (!list) return;
  let selectedIndex = null;
  
  list.querySelectorAll('.seq-btn').forEach((btn) => {
    btn.addEventListener('click', (event) => {
      const targetIndex = Number(event.currentTarget.dataset.index);
      if (selectedIndex === null) {
          selectedIndex = targetIndex;
          event.currentTarget.style.borderColor = '#58a6ff';
          event.currentTarget.style.background = '#0d419d';
      } else {
          if (selectedIndex !== targetIndex) {
              const updated = [...sequenceState.currentOrder];
              const temp = updated[selectedIndex];
              updated[selectedIndex] = updated[targetIndex];
              updated[targetIndex] = temp;
              sequenceState.currentOrder = updated;

              list.innerHTML = updated.map((name, index) => `<button class="seq-btn" data-index="${index}" style="padding:15px; font-size:18px; cursor:pointer; border:2px solid #30363d; background:#21262d; color:#c9d1d9; border-radius:8px;">${name}</button>`).join('');
              enableClickSwap();
          } else {
              // Deselect
              selectedIndex = null;
              event.currentTarget.style.borderColor = '#30363d';
              event.currentTarget.style.background = '#21262d';
          }
      }
    });
  });
}



function safeJsonParse(text) {
  try {
    return JSON.parse(text);
  } catch (error) {
    return { error: 'JSON 解析失败', raw: text };
  }
}

function setupSchulte(task) {
  const size = task.size;
  window.schulteExpected = 1;
  window.schulteCompleted = false;
  
  const grid = document.createElement('div');
  grid.className = 'schulte-grid';
  grid.style.display = 'grid';
  grid.style.gridTemplateColumns = `repeat(${size}, 1fr)`;
  grid.style.gap = '8px';
  grid.style.maxWidth = '400px';
  grid.style.margin = '0 auto';

  task.grid.forEach(num => {
    const btn = document.createElement('button');
    btn.className = 'schulte-cell';
    btn.textContent = num;
    btn.style.padding = '20px';
    btn.style.fontSize = '24px';
    btn.onclick = () => {
      if (num === window.schulteExpected) {
        btn.style.background = '#238636';
        btn.style.color = 'white';
        window.schulteExpected++;
        if (window.schulteExpected > size * size) {
          window.schulteCompleted = true;
          statusEl.textContent = '舒尔特方格完成，请点击提交！';
        }
      } else {
        btn.style.background = '#dc3545';
        setTimeout(() => btn.style.background = '', 300);
      }
    };
    grid.appendChild(btn);
  });
  questionEl.innerHTML = '';
  questionEl.appendChild(grid);
}

function setupStroop(task) {
  window.stroopIndex = 0;
  window.stroopCorrect = 0;
  
  const container = document.createElement('div');
  container.className = 'stroop-container';
  container.style.textAlign = 'center';
  
  const wordDisplay = document.createElement('div');
  wordDisplay.style.fontSize = '48px';
  wordDisplay.style.fontWeight = 'bold';
  wordDisplay.style.margin = '30px 0';
  
  const optionsDiv = document.createElement('div');
  optionsDiv.style.display = 'flex';
  optionsDiv.style.justifyContent = 'center';
  optionsDiv.style.gap = '10px';
  optionsDiv.style.flexWrap = 'wrap';
  
  const nextTrial = () => {
    if (window.stroopIndex >= task.trials.length) {
      wordDisplay.textContent = `测试完成！正确数: ${window.stroopCorrect}/${task.trials.length}`;
      optionsDiv.style.display = 'none';
      return;
    }
    const trial = task.trials[window.stroopIndex];
    wordDisplay.textContent = trial.text;
    wordDisplay.style.color = trial.color;
    
    optionsDiv.innerHTML = '';
    task.colorsPool.forEach(c => {
      const btn = document.createElement('button');
      btn.textContent = c.label;
      btn.onclick = () => {
        if (c.name === trial.colorName) {
            window.stroopCorrect++;
        }
        window.stroopIndex++;
        nextTrial();
      };
      optionsDiv.appendChild(btn);
    });
  };
  
  container.appendChild(wordDisplay);
  container.appendChild(optionsDiv);
  questionEl.innerHTML = '';
  questionEl.appendChild(container);
  nextTrial();
}

function setupMirror(task) {
  window.mirrorCorrect = false;
  const container = document.createElement('div');
  container.style.display = 'flex';
  container.style.justifyContent = 'space-around';
  container.style.gap = '20px';
  
  const renderGrid = (isLeft) => {
    const grid = document.createElement('div');
    grid.style.display = 'grid';
    grid.style.gridTemplateColumns = 'repeat(4, 50px)';
    grid.style.gridTemplateRows = 'repeat(4, 50px)';
    grid.style.gap = '5px';
    for(let y=1; y<=4; y++) {
      for(let x=1; x<=4; x++) {
        const cell = document.createElement('div');
        cell.style.background = '#161b22';
        cell.style.border = '1px solid #30363d';
        cell.style.cursor = 'pointer';
        cell.dataset.x = isLeft ? x : 5 - x; // mirror x
        cell.dataset.y = y;
        
        let isTarget = task.points.some(p => p.x === (isLeft ? x : 5-x) && p.y === y);
        if(isLeft && isTarget) {
            cell.style.background = '#1f6feb';
        }
        if(!isLeft) {
            cell.dataset.active = "false";
            cell.onclick = () => {
                const isActive = cell.dataset.active === "true";
                cell.dataset.active = isActive ? "false" : "true";
                cell.style.background = isActive ? '#161b22' : '#1f6feb';
                checkMirror();
            };
        }
        grid.appendChild(cell);
      }
    }
    return grid;
  };

  const rightGrid = renderGrid(false);
  const checkMirror = () => {
    let correct = true;
    for(let y=1; y<=4; y++){
      for(let x=1; x<=4; x++){
        let isTarget = task.points.some(p => p.x === x && p.y === y);
        let cell = rightGrid.querySelector(`[data-x="${x}"][data-y="${y}"]`);
        let isActive = cell.dataset.active === "true";
        if(isTarget !== isActive) correct = false;
      }
    }
    window.mirrorCorrect = correct;
    if(correct) statusEl.textContent = "镜像图形匹配正确，请提交！";
  }
  
  container.appendChild(renderGrid(true));
  container.appendChild(rightGrid);
  questionEl.innerHTML = '';
  questionEl.appendChild(container);
}

function setupMemoryStory(task) {
  window.memoryStorySelections = 0;
  const container = document.createElement('div');
  
  const displayArea = document.createElement('div');
  displayArea.style.fontSize = '24px';
  displayArea.style.margin = '20px 0';
  displayArea.style.padding = '20px';
  displayArea.style.background = '#161b22';
  displayArea.style.borderRadius = '10px';
  // Use inline items blocks to avoid weird layout
  displayArea.innerHTML = task.items.map(i => `<span style="display:inline-block; margin:10px; background:#21262d; border:1px solid #30363d; padding:10px; border-radius:8px; box-shadow:0 2px 4px rgba(0,0,0,0.2); color:#c9d1d9;">${i.name}</span>`).join('');
  
  container.appendChild(displayArea);
  questionEl.innerHTML = '';
  questionEl.appendChild(container);
  
  setTimeout(() => {
    displayArea.innerHTML = "时间到，请回忆刚才出现的物品并全部点击出来：";
    displayArea.style.background = 'transparent';
    const optionsDiv = document.createElement('div');
    optionsDiv.style.display = 'grid';
    optionsDiv.style.gridTemplateColumns = 'repeat(auto-fit, minmax(100px, 1fr))';
    optionsDiv.style.gap = '15px';
    optionsDiv.style.marginTop = '20px';
    
    // Mix with other items (using unicode escapes for icons to avoid encoding issues)
    const allPool = [...task.items];
    const dummy = [
      {name:"火箭"},
      {name:"台灯"},
      {name:"剪刀"},
      {name:"电脑"},
      {name:"苹果"},
      {name:"足球"},
      {name:"铃铛"},
      {name:"星星"}
    ];
    allPool.push(...dummy);
    allPool.sort(() => Math.random() - 0.5);

    allPool.forEach(i => {
        const btn = document.createElement('button');
        btn.innerHTML = `<div style="font-size:24px; padding:10px 0;">${i.name}</div>`;
        btn.style.padding = '15px 5px';
        btn.style.border = '2px solid #30363d';
        btn.style.borderRadius = '8px';
        btn.style.background = '#21262d';
        btn.style.color = '#c9d1d9';
        btn.style.cursor = 'pointer';
        
        btn.onclick = () => {
            if(btn.disabled) return;
            btn.disabled = true;
            if(task.items.some(ti => ti.name === i.name)){
                btn.style.borderColor = '#2ea043';
                btn.style.background = '#053814';
                window.memoryStorySelections++;
                if (window.memoryStorySelections >= task.items.length) {
                    statusEl.textContent = '情景物品全部找齐！请提交结果。';
                }
            } else {
                btn.style.borderColor = '#f85149';
                btn.style.background = '#490202';
            }
        };
        optionsDiv.appendChild(btn);
    });
    container.appendChild(optionsDiv);
  }, 3000);
}
