% Specify server uptime in second, a negative value denotes
% infinite time
SERVER_UPTIME = 30;

% Socket port number
SERVER_PORT = 3000;

exit = false;

fprintf('Start server\n');

% FIXME: Clean up function not called
obj = onCleanup(@() cleanUpFunc);

% Free server resources if existed
if (isExist('server'))
    delete(server);
end

% Listen incoming connection
server = tcpserver(SERVER_PORT);

fprintf('Listening\n');

startTime = clock;

while ~exit && (etime(clock, startTime) < SERVER_UPTIME || ...
        SERVER_UPTIME < 0)
    % pause(1);

    try
        line = readline(server);
        
        if ~isempty(line) && strlength(line) > 0
            % Extract header and payload from incoming data
            param1 = split(line, ':');
            param2 = [];

            % Check header of incoming data
            if length(param1) == 2 && strcmp(param1{1, 1}, 'algo_data') == 1
                % Extract elements from payload
                param2 = split(param1{2, 1}, ',');

                % Check payload of incoming data
                if length(param2) ~= 9
                    fprintf('Failed to extract payload: %s\n', line);
                    param2 = [];
                end
            else
                fprintf('Failed to extract received data: %s\n', line);
            end

            if ~isempty(param2)
                fprintf('Data extracted: %s\n', line);

                % TODO: Call algorithm to get its results, replace test output below
                px = 0.11119991111;
                py = 0.22229992222;
                pz = 0.33339993333;
                ax = 0.44449994444;
                ay = 0.55559995555;
                az = 0.66669996666;

                % Format location results to a message
                result = strcat('algo_result:', ...
                    num2str(px, 16), ',', ...
                    num2str(py, 16), ',', ...
                    num2str(pz, 16), ',', ...
                    num2str(ax, 16), ',', ...
                    num2str(ay, 16), ',', ...
                    num2str(az, 16));

                % Send location results to the client
                writeline(server, result);
            end
        end
    catch e
        fprintf('%s\n', e.message);
    end
end

delete(server);

fprintf('Server terminated after %.2f second(s)\n', ...
    etime(clock, startTime));

% Check if a variable exists in Workspace
function x = isExist(var)
    x = evalin('base', sprintf('exist(''%s'')', var)) == 1;
end

function cleanUpFunc()
    exit = true;

    fprintf('Terminating server\n');
end