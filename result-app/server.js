// parsing rediscloud credentials
var vcap_services = process.env.VCAP_SERVICES;
var rediscloud_service = JSON.parse(vcap_services)["p-mysql"][0]
var credentials = rediscloud_service.credentials;


var express = require('express'),
    async = require('async'),
    pg = require("pg"),
    mysql = require("mysql"),
    cookieParser = require('cookie-parser'),
    bodyParser = require('body-parser'),
    methodOverride = require('method-override'),
    app = express(),
    server = require('http').Server(app),
    io = require('socket.io')(server);

io.set('transports', ['polling']);

var port = process.env.PORT || 4000;

io.sockets.on('connection', function (socket) {

  socket.emit('message', { text : 'Welcome!' });

  socket.on('subscribe', function (data) {
    socket.join(data.channel);
  });
});

async.retry(
  {times: 1000, interval: 1000},
  function(callback) {
    //pg.connect(credentials.uri, function(err, client, done) {
    //  if (err) {
    //    console.error("Failed to connect to db");
    //  }
    //  callbacon.query('SELECT * from user LIMIT 2', function(err, rows, fields) {uient);
    //});
    var connection = mysql.createConnection({
        host     : credentials.hostname,
        user     : credentials.username,
        password : credentials.password,
        database : credentials.name,
        port     : credentials.port
    });
    
    connection.connect(function(err){
        if(!err) {
            console.log("Database is connected ... \n\n");
            callback(err, connection); 
        }
        else {
            console.log("Error connecting database ... \n\n");
        }
    });
  },
  function(err, client) {
    if (err) {
      return console.err("Giving up");
    }
    client.query('SELECT vote, COUNT(id) AS count FROM votes GROUP BY vote',[], function(err,rows){
        if(err) throw err;
        console.log('Data received from Db:\n');
        console.log(rows);
    }); 
    console.log("Connected to db at function (err, client)"); 
    getVotes(client);
  }
);

function getVotes(client) {
  console.log("counting votes");
  client.query('SELECT vote, COUNT(id) AS count FROM votes GROUP BY vote', [], function(err, result) {
    console.log("counting votes breakpoint 2");
    if (err) {
      console.error("Error performing query: " + err);
    } else {
      console.log(result);
      var data = result.rows.reduce(function(obj, row) {
        obj[row.vote] = row.count;
        return obj;
      }, {});
      io.sockets.emit("scores", JSON.stringify(data));
    }

    setTimeout(function() {getVotes(client) }, 1000);
  });
}

app.use(cookieParser());
app.use(bodyParser());
app.use(methodOverride('X-HTTP-Method-Override'));
app.use(function(req, res, next) {
  res.header("Access-Control-Allow-Origin", "*");
  res.header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
  res.header("Access-Control-Allow-Methods", "PUT, GET, POST, DELETE, OPTIONS");
  next();
});

app.use(express.static(__dirname + '/views'));

app.get('/', function (req, res) {
  res.sendFile(path.resolve(__dirname + '/views/index.html'));
});

server.listen(port, function () {
  var port = server.address().port;
  console.log('App running on port ' + port);
});
