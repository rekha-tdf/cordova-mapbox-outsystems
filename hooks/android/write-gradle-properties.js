module.exports = function (context) {
  var fs = require('fs');
  var path = require('path');
  var platformRoot = path.join(context.opts.projectRoot, 'platforms', 'android');
  var propsPath = path.join(platformRoot, 'gradle.properties');

  var token = context.opts.plugin ? context.opts.plugin.variables.MAPBOX_DOWNLOADS_TOKEN : null;
  token = token || process.env.MAPBOX_DOWNLOADS_TOKEN;

  if (!token) {
    console.log('[MapboxPlugin] MAPBOX_DOWNLOADS_TOKEN not set — skipping gradle.properties');
    return;
  }

  var line = 'MAPBOX_DOWNLOADS_TOKEN=' + token + '\n';
  fs.appendFileSync(propsPath, line);
  console.log('[MapboxPlugin] Wrote MAPBOX_DOWNLOADS_TOKEN to gradle.properties');
};