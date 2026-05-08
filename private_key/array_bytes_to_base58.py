import base58
import json

def convert_solana_key(file_path):
    try:
        with open(file_path, 'r') as f:
            byte_array = json.load(f)
            secret_key = base58.b58encode(bytes(byte_array)).decode('utf-8')
            return secret_key
    except Exception as e:
        return f"Ошибка: {e}"

print(f"Твой Private Key: {convert_solana_key('id.json')}")