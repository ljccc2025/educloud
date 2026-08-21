local bucketCount = #KEYS
if bucketCount == 0 or #ARGV ~= bucketCount * 3 then
  return redis.error_reply('invalid token bucket arguments')
end

local redisTime = redis.call('TIME')
local now = tonumber(redisTime[1]) * 1000 + math.floor(tonumber(redisTime[2]) / 1000)
local computed = {}
local maxWait = 0

for index = 1, bucketCount do
  local argument = (index - 1) * 3
  local requests = tonumber(ARGV[argument + 1])
  local period = tonumber(ARGV[argument + 2])
  local burst = tonumber(ARGV[argument + 3])
  if not requests or not period or not burst or requests <= 0 or period <= 0 or burst < requests then
    return redis.error_reply('invalid token bucket rule')
  end

  local exists = redis.call('EXISTS', KEYS[index])
  local tokens = burst
  local timestamp = now
  if exists == 1 then
    local state = redis.call('HMGET', KEYS[index], 'tokens', 'timestamp')
    local storedTtl = redis.call('PTTL', KEYS[index])
    tokens = tonumber(state[1])
    timestamp = tonumber(state[2])
    if not tokens or not timestamp or storedTtl < 0
        or tokens < 0 or tokens > burst or timestamp < 0 or timestamp > now then
      return redis.error_reply('corrupt token bucket state')
    end
  end
  local elapsed = math.max(0, now - timestamp)
  local available = math.min(burst, tokens + (elapsed * requests / period))
  local ttl = math.max(period, math.ceil(burst * period / requests))
  computed[index] = { available = available, ttl = ttl }

  if available < 1 then
    local wait = math.ceil((1 - available) * period / requests)
    maxWait = math.max(maxWait, wait)
  end
end

if maxWait > 0 then
  return {0, maxWait}
end

for index = 1, bucketCount do
  redis.call('HSET', KEYS[index],
    'tokens', computed[index].available - 1,
    'timestamp', now)
  redis.call('PEXPIRE', KEYS[index], computed[index].ttl)
end

return {1, 0}
