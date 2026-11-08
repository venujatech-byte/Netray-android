with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

# Fix: replace selectedAbis references with releaseAbis
content = content.replace('selectedAbis', 'releaseAbis')

# The ndk block uses abiFilters += selectedAbis - need to change to releaseAbis
content = content.replace('abiFilters += selectedAbis', 'abiFilters += releaseAbis')

# Remove the old selectedAbis line
lines = content.split('\n')
new_lines = []
for i, line in enumerate(lines):
    if 'val selectedAbis' in line:
        continue  # skip this line
    new_lines.append(line)

content = '\n'.join(new_lines)

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)

print("Fixed build.gradle.kts")
