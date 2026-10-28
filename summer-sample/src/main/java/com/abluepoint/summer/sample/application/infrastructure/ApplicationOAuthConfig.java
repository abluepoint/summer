package com.abluepoint.summer.sample.application.infrastructure;

import com.abluepoint.summer.sample.application.application.*;
import java.util.*;
import java.time.Instant;

public record ApplicationOAuthConfig(List<String> grants,List<String> responses,List<String> scopes,String auth,boolean pkce,boolean rotation,List<String> redirectUris,long version){}
