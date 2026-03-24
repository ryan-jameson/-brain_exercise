import sys
import json
import urllib.request
import os

try:
    import config
    API_KEY = getattr(config, 'API_KEY', '')
    BASE_URL = getattr(config, 'BASE_URL', 'https://api.chatanywhere.org/v1/chat/completions')
    MODEL_NAME = getattr(config, 'MODEL_NAME', 'gpt-4o-mini')
    PROXY_URL = getattr(config, 'PROXY_URL', '')
except ImportError:
    API_KEY = os.environ.get('AI_API_KEY', '')
    BASE_URL = os.environ.get('AI_BASE_URL', 'https://api.chatanywhere.org/v1/chat/completions')
    MODEL_NAME = os.environ.get('AI_MODEL_NAME', 'gpt-4o-mini')
    PROXY_URL = os.environ.get('AI_PROXY_URL', '')

def print_headers():
    pass

def send_error(msg):
    print_headers()
    print(json.dumps({"error": msg}, ensure_ascii=False))
    sys.exit(0)

def main():
    method = os.environ.get('REQUEST_METHOD', 'POST')
    
    if method != 'POST':
        # Defaulting to reading everything from stdin as simple CGI wrapper might not set REQUEST_METHOD properly
        pass

    try:
        raw_data = sys.stdin.read().strip()
        if not raw_data:
            send_error("没有接收到数据")
        
        data = json.loads(raw_data)
    except Exception as e:
        send_error(f"无效的 JSON 输入: {e}")
        
    prompt = "请作为专业的脑部认知训练助理，根据以下用户认知训练游戏历史记录数据，用一小段精简的中文（大约100-150字）帮我总结近期的训练表现情况，并给出简单的建议。不要使用Markdown加粗或其他格式，直接返回纯文本：\n\n数据：\n"
    prompt += json.dumps(data, ensure_ascii=False)
    
    req_body = {
        "model": MODEL_NAME,
        "messages": [
            {"role": "system", "content": "你是一个专业的脑部认知训练分析助手。"},
            {"role": "user", "content": prompt}
        ],
        "temperature": 0.7
    }
    
    req_data = json.dumps(req_body).encode('utf-8')
    req = urllib.request.Request(BASE_URL, data=req_data)
    req.add_header('Content-Type', 'application/json')
    req.add_header('Authorization', f'Bearer {API_KEY}')
    # 必须要加 User-Agent，因为 ChatAnywhere 会拦截默认的 Python HTTP 客户端头导致返回 403 Forbidden
    req.add_header('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36')
    
    if PROXY_URL:
        proxy_handler = urllib.request.ProxyHandler({'http': PROXY_URL, 'https': PROXY_URL})
        opener = urllib.request.build_opener(proxy_handler)
    else:
        opener = urllib.request.build_opener()
    
    try:
        response = opener.open(req, timeout=30)
        res_body = response.read().decode('utf-8')
        res_json = json.loads(res_body)
        
        choices = res_json.get('choices', [])
        if choices and len(choices) > 0:
            reply = choices[0].get('message', {}).get('content', 'AI 请求没有返回正确的结果。')
        else:
            reply = '未获取到AI总结结果。'
            
        print_headers()
        print(json.dumps({"summary": reply.strip()}, ensure_ascii=False))
    except Exception as e:
        send_error(f"调用 AI 接口失败: {e}")
        
if __name__ == "__main__":
    main()