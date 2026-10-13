with open(r'c:\Users\24eg1\OneDrive\Desktop\TrustLend\backend\src\main\java\app\services\AiAssistantService.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

depth = 0
for idx, line in enumerate(lines, 1):
    in_str = False
    in_char = False
    i = 0
    while i < len(line):
        ch = line[i]
        if line[i:i+2] == '//' and not in_str and not in_char:
            break
        if ch == '"' and not in_char and (i == 0 or line[i-1] != '\\'):
            in_str = not in_str
        elif ch == "'" and not in_str and (i == 0 or line[i-1] != '\\'):
            in_char = not in_char
        elif not in_str and not in_char:
            if ch == '{':
                depth += 1
            elif ch == '}':
                depth -= 1
        i += 1
    if depth < 1 and idx < len(lines):
        print(f"Warning: depth dropped to {depth} at line {idx}: {line.strip()[:60]}")
    if idx in [543, 582, 608, 634, 636, 706, 716, 717, 725, 731, 742, 752, 953, 954, 957, 958, 959, 960, 961]:
        print(f"{idx}: depth={depth} | {line.strip()[:60]}")
print("Final depth:", depth)
