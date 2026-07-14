var e=e=>{switch(e){case`index`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=index,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        label="\\N",
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    user [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">User</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Platform user</FONT></TD></TR></TABLE>>,
        likec4_id=user,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    mediaplatform [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">media-platform</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Render platform</FONT></TD></TR></TABLE>>,
        likec4_id=mediaPlatform,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    user -> mediaplatform [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">uses</FONT></TD></TR></TABLE>>,
        likec4_id="77kvz4",
        minlen=1,
        style=dashed];
    reviewer [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Human Reviewer</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Final arbiter</FONT></TD></TR></TABLE>>,
        likec4_id=reviewer,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    hermes [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Hermes Control Plane</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Multi-agent orchestration</FONT></TD></TR></TABLE>>,
        likec4_id=hermes,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    reviewer -> hermes [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">reviews</FONT></TD></TR></TABLE>>,
        likec4_id="1puz3yb",
        minlen=1,
        style=dashed];
    cloudflare [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Cloudflare</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">R2, Pages, AI Gateway</FONT></TD></TR></TABLE>>,
        likec4_id=cloudflare,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    hermes -> cloudflare [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">publishes</FONT></TD></TR></TABLE>>,
        likec4_id=swbi77,
        minlen=1,
        style=dashed];
    telegram [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Telegram</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Notifications</FONT></TD></TR></TABLE>>,
        likec4_id=telegram,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    hermes -> telegram [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">notifies</FONT></TD></TR></TABLE>>,
        likec4_id="47if73",
        minlen=1,
        style=dashed];
    hermes -> mediaplatform [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">develops</FONT></TD></TR></TABLE>>,
        likec4_id="17mj72t",
        style=dashed];
    aiproviders [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">AI Providers</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">MiMo, LongCat, OpenRouter, etc.</FONT></TD></TR></TABLE>>,
        likec4_id=aiProviders,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    mediaplatform -> aiproviders [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">calls models</FONT></TD></TR></TABLE>>,
        likec4_id="1kprbf",
        minlen=1,
        style=dashed];
    storage [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Object storage / shared filesystem</FONT></TD></TR></TABLE>>,
        likec4_id=storage,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    mediaplatform -> storage [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">reads/writes</FONT></TD></TR></TABLE>>,
        likec4_id="13h0zjc",
        minlen=1,
        style=dashed];
}
`;case`systemContext`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=systemContext,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        label="\\N",
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    subgraph cluster_mediaplatform {
        graph [color="#1b3d88",
            fillcolor="#194b9e",
            label=<<FONT POINT-SIZE="11" COLOR="#bfdbfeb3"><B>MEDIA-PLATFORM</B></FONT>>,
            likec4_depth=1,
            likec4_id=mediaPlatform,
            likec4_level=0,
            margin=40,
            style=filled
        ];
        opencue [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">OpenCue</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">ExecutionEnvironment only — NOT a Provider</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.opencue",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        remotion [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Remotion</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Non-production/POC subtitle template provider</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.remotion",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
    }
    opencue -> remotion [style=invis];
}
`;case`containerDiagram`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=containerDiagram,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        label="\\N",
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    subgraph cluster_mediaplatform {
        graph [color="#1b3d88",
            fillcolor="#194b9e",
            label=<<FONT POINT-SIZE="11" COLOR="#bfdbfeb3"><B>MEDIA-PLATFORM</B></FONT>>,
            likec4_depth=1,
            likec4_id=mediaPlatform,
            likec4_level=0,
            margin=40,
            style=filled
        ];
        platformapp [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">platform-app</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Spring Boot entry point</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.platformApp",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        aimodule [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">ai-module</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">AI integration</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.aiModule",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        rendermodule [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">render-module</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Core render domain</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        storagemodule [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">storage-module</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Storage delivery and materialization</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.storageModule",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        ingestmodule [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">ingest-module</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Upload preflight and metadata detection</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.ingestModule",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        opencue [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">OpenCue</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">ExecutionEnvironment only — NOT a Provider</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.opencue",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        remotion [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Remotion</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Non-production/POC subtitle template provider</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.remotion",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        sharedkernel [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">shared-kernel</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Shared domain primitives</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.sharedKernel",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
    }
    platformapp -> rendermodule [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">uses</FONT></TD></TR></TABLE>>,
        likec4_id=ap697o,
        style=dashed,
        weight=2];
    platformapp -> storagemodule [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">uses</FONT></TD></TR></TABLE>>,
        likec4_id=y4e57b,
        style=dashed,
        weight=2];
    platformapp -> ingestmodule [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">uses</FONT></TD></TR></TABLE>>,
        likec4_id="16tgm98",
        style=dashed];
    aimodule -> sharedkernel [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">depends on</FONT></TD></TR></TABLE>>,
        likec4_id="18eum16",
        minlen=1,
        style=dashed];
    rendermodule -> opencue [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes ExecutionEnv only</FONT></TD></TR></TABLE>>,
        likec4_id="16zyo0t",
        minlen=1,
        style=dashed,
        weight=2];
    rendermodule -> remotion [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes POC only</FONT></TD></TR></TABLE>>,
        likec4_id="1rv9fdf",
        minlen=1,
        style=dashed,
        weight=2];
    storage [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Object storage / shared filesystem</FONT></TD></TR></TABLE>>,
        likec4_id=storage,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    rendermodule -> storage [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">persists artifacts</FONT></TD></TR></TABLE>>,
        likec4_id="99aifq",
        style=dashed];
    rendermodule -> sharedkernel [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">depends on</FONT></TD></TR></TABLE>>,
        likec4_id="86h9qg",
        style=dashed,
        weight=2];
    storagemodule -> storage [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">R2/S3-compatible</FONT></TD></TR></TABLE>>,
        likec4_id="1x89xad",
        style=dashed];
    storagemodule -> sharedkernel [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">depends on</FONT></TD></TR></TABLE>>,
        likec4_id="8jw69n",
        style=dashed,
        weight=2];
    ingestmodule -> sharedkernel [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">depends on</FONT></TD></TR></TABLE>>,
        likec4_id="2burr4",
        style=dashed];
}
`;case`controlPlane`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=controlPlane,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        label="\\N",
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    subgraph cluster_hermes {
        graph [color="#1b3d88",
            fillcolor="#194b9e",
            label=<<FONT POINT-SIZE="11" COLOR="#bfdbfeb3"><B>HERMES CONTROL PLANE</B></FONT>>,
            likec4_depth=1,
            likec4_id=hermes,
            likec4_level=0,
            margin=40,
            style=filled
        ];
        {
            graph [rank=same];
            hermesagent [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Hermes Agent</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Gateway, orchestrator, Level 3 Feature<BR/>Coordinator</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.hermesAgent",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
            codingagents [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Coding Agents</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">OpenCode, Codex, Kilo Code, Claude Code,<BR/>Aider</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.codingAgents",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
            reviewinfra [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Review Infrastructure</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">CODEOWNERS, Semgrep, Review Packets</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.reviewInfra",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
        }
        {
            graph [rank=same];
            policies [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Policies</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Permissions, stop conditions</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.policies",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
            dashboard [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Dashboard</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">scribe.cc.cd</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.dashboard",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
        }
        hermesagent -> policies [style=invis];
    }
    codingagents -> reviewinfra [style=invis];
    reviewinfra -> dashboard [style=invis];
}
`;case`controlPlaneContext`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=controlPlaneContext,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        label="\\N",
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    subgraph cluster_hermes {
        graph [color="#1b3d88",
            fillcolor="#194b9e",
            label=<<FONT POINT-SIZE="11" COLOR="#bfdbfeb3"><B>HERMES CONTROL PLANE</B></FONT>>,
            likec4_depth=1,
            likec4_id=hermes,
            likec4_level=0,
            margin=40,
            style=filled
        ];
        {
            graph [rank=same];
            hermesagent [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Hermes Agent</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Gateway, orchestrator, Level 3 Feature<BR/>Coordinator</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.hermesAgent",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
            codingagents [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Coding Agents</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">OpenCode, Codex, Kilo Code, Claude Code,<BR/>Aider</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.codingAgents",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
            reviewinfra [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Review Infrastructure</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">CODEOWNERS, Semgrep, Review Packets</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.reviewInfra",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
        }
        {
            graph [rank=same];
            policies [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Policies</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Permissions, stop conditions</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.policies",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
            dashboard [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Dashboard</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">scribe.cc.cd</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.dashboard",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
        }
        hermesagent -> policies [minlen=1,
            style=invis];
    }
    codingagents -> reviewinfra [style=invis];
    cloudflare [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Cloudflare</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">R2, Pages, AI Gateway</FONT></TD></TR></TABLE>>,
        likec4_id=cloudflare,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    dashboard -> cloudflare [arrowhead=normal,
        likec4_id=swbi77,
        ltail=cluster_hermes,
        minlen=1,
        style=dashed,
        xlabel=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">publishes</FONT></TD></TR></TABLE>>];
    telegram [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Telegram</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Notifications</FONT></TD></TR></TABLE>>,
        likec4_id=telegram,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    dashboard -> telegram [arrowhead=normal,
        likec4_id="47if73",
        ltail=cluster_hermes,
        minlen=1,
        style=dashed,
        xlabel=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">notifies</FONT></TD></TR></TABLE>>];
    mediaplatform [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">media-platform</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Render platform</FONT></TD></TR></TABLE>>,
        likec4_id=mediaPlatform,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    dashboard -> mediaplatform [arrowhead=normal,
        likec4_id="17mj72t",
        ltail=cluster_hermes,
        style=dashed,
        xlabel=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">develops</FONT></TD></TR></TABLE>>];
    user [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">User</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Platform user</FONT></TD></TR></TABLE>>,
        likec4_id=user,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    user -> mediaplatform [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">uses</FONT></TD></TR></TABLE>>,
        likec4_id="77kvz4",
        minlen=0,
        style=dashed];
    reviewer [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Human Reviewer</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Final arbiter</FONT></TD></TR></TABLE>>,
        likec4_id=reviewer,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    reviewer -> hermesagent [arrowhead=normal,
        lhead=cluster_hermes,
        likec4_id="1puz3yb",
        minlen=1,
        style=dashed,
        xlabel=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">reviews</FONT></TD></TR></TABLE>>];
}
`;case`vs0VerticalSlice`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=vs0VerticalSlice,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        label="\\N",
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    subgraph cluster_rendermodule {
        graph [color="#1b3d88",
            fillcolor="#194b9e",
            label=<<FONT POINT-SIZE="11" COLOR="#bfdbfeb3"><B>RENDER-MODULE</B></FONT>>,
            likec4_depth=1,
            likec4_id="mediaPlatform.renderModule",
            likec4_level=0,
            margin=40,
            style=filled
        ];
        timelineedit [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Timeline Edit Command</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">TL.0: Sealed interface with 12 typed command<BR/>records</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.timelineEdit",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        faketestlayer [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Fake Test Layer</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">VS.1-TEST.1: Fake implementations for<BR/>integration testing</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.fakeTestLayer",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        captiontemplate [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Caption Template</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">CT.0: Typed intent model for caption/subtitle<BR/>rendering</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.captionTemplate",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        previewrenderjobservice [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Preview Render Job Service</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">VS.1: Preview render job lifecycle</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.previewRenderJobService",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        previewartifactqueryservice [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Preview Artifact Query Service</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">VS.1: Product/artifact metadata query</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.previewArtifactQueryService",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        assstylemapper [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">AssStyleMapper</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">CT.0: Maps CaptionTemplateSpec to ASS format<BR/>parameters</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.assStyleMapper",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        providerbinding [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Provider Binding</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Deterministic eligibility + priority provider<BR/>selection</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.providerBinding",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        ffmpegbaseline [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">FFmpeg/libass Baseline</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Production rendering baseline</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.ffmpegBaseline",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        productruntime [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Product Runtime</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Product lifecycle management</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.productRuntime",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        storageruntime [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage Runtime</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Storage/materialization management</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.storageRuntime",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
    }
    platformapp [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">platform-app</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Spring Boot entry point</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.platformApp",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    platformapp -> previewrenderjobservice [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">exposes API</FONT></TD></TR></TABLE>>,
        likec4_id="1mg8wls",
        style=dashed];
    platformapp -> previewartifactqueryservice [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">exposes API</FONT></TD></TR></TABLE>>,
        likec4_id="1ubxxqh",
        style=dashed];
    timelineedit -> captiontemplate [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">generates typed intent</FONT></TD></TR></TABLE>>,
        likec4_id="1e9y3o3",
        minlen=1,
        style=dashed];
    faketestlayer -> previewrenderjobservice [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">tests</FONT></TD></TR></TABLE>>,
        likec4_id="1xkwqek",
        style=dashed,
        weight=2];
    faketestlayer -> previewartifactqueryservice [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">tests</FONT></TD></TR></TABLE>>,
        likec4_id="1p2jn05",
        style=dashed,
        weight=2];
    captiontemplate -> assstylemapper [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">maps to ASS parameters</FONT></TD></TR></TABLE>>,
        likec4_id="1ojuwrd",
        style=dashed];
    previewrenderjobservice -> providerbinding [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">compiles plan</FONT></TD></TR></TABLE>>,
        likec4_id=c8vxsg,
        style=dashed,
        weight=2];
    previewrenderjobservice -> ffmpegbaseline [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">executes preview</FONT></TD></TR></TABLE>>,
        likec4_id=w0x3as,
        style=dashed,
        weight=2];
    previewrenderjobservice -> productruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">creates product</FONT></TD></TR></TABLE>>,
        likec4_id="1jv1scn",
        style=dashed,
        weight=2];
    previewartifactqueryservice -> productruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">queries product</FONT></TD></TR></TABLE>>,
        likec4_id="1rm1ffi",
        style=dashed,
        weight=2];
    previewartifactqueryservice -> storageruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">queries storage</FONT></TD></TR></TABLE>>,
        likec4_id=at7efg,
        style=dashed,
        weight=3];
    assstylemapper -> providerbinding [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">resolves provider</FONT></TD></TR></TABLE>>,
        likec4_id=dyb7bb,
        style=dashed];
    providerbinding -> ffmpegbaseline [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes PRODUCTION</FONT></TD></TR></TABLE>>,
        likec4_id=q47oy6,
        style=dashed];
    ffmpegbaseline -> productruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">produces output</FONT></TD></TR></TABLE>>,
        likec4_id="4joqk9",
        style=dashed];
    productruntime -> storageruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">manages lifecycle</FONT></TD></TR></TABLE>>,
        likec4_id="1h6i8bc",
        style=dashed,
        weight=3];
    storage [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Object storage / shared filesystem</FONT></TD></TR></TABLE>>,
        likec4_id=storage,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    storageruntime -> storage [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">persists artifacts</FONT></TD></TR></TABLE>>,
        likec4_id="1c6zqd9",
        minlen=1,
        style=dashed];
}
`;case`captionTemplateBoundary`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=captionTemplateBoundary,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        label="\\N",
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    timelineedit [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Timeline Edit Command</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">TL.0: Sealed interface with 12 typed command<BR/>records</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.timelineEdit",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    captiontemplate [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Caption Template</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">CT.0: Typed intent model for caption/subtitle<BR/>rendering</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.captionTemplate",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    timelineedit -> captiontemplate [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">generates typed intent</FONT></TD></TR></TABLE>>,
        likec4_id="1e9y3o3",
        minlen=1,
        style=dashed];
    assstylemapper [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">AssStyleMapper</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">CT.0: Maps CaptionTemplateSpec to ASS format<BR/>parameters</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.assStyleMapper",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    captiontemplate -> assstylemapper [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">maps to ASS parameters</FONT></TD></TR></TABLE>>,
        likec4_id="1ojuwrd",
        style=dashed];
    providerbinding [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Provider Binding</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Deterministic eligibility + priority provider<BR/>selection</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.providerBinding",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    assstylemapper -> providerbinding [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">resolves provider</FONT></TD></TR></TABLE>>,
        likec4_id=dyb7bb,
        style=dashed];
    ffmpegbaseline [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">FFmpeg/libass Baseline</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Production rendering baseline</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.ffmpegBaseline",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    providerbinding -> ffmpegbaseline [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes PRODUCTION</FONT></TD></TR></TABLE>>,
        likec4_id=q47oy6,
        minlen=1,
        style=dashed];
}
`;case`productStorageBoundary`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=productStorageBoundary,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        label="\\N",
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    ffmpegbaseline [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">FFmpeg/libass Baseline</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Production rendering baseline</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.ffmpegBaseline",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    productruntime [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Product Runtime</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Product lifecycle management</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.productRuntime",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    ffmpegbaseline -> productruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">produces output</FONT></TD></TR></TABLE>>,
        likec4_id="4joqk9",
        minlen=1,
        style=dashed];
    storageruntime [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage Runtime</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Storage/materialization management</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.storageRuntime",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    productruntime -> storageruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">manages lifecycle</FONT></TD></TR></TABLE>>,
        likec4_id="1h6i8bc",
        style=dashed,
        weight=3];
    storage [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Object storage / shared filesystem</FONT></TD></TR></TABLE>>,
        likec4_id=storage,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    storageruntime -> storage [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">persists artifacts</FONT></TD></TR></TABLE>>,
        likec4_id="1c6zqd9",
        minlen=1,
        style=dashed];
}
`;case`providerExecutionBoundary`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=providerExecutionBoundary,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        label="\\N",
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    providerbinding [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Provider Binding</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Deterministic eligibility + priority provider<BR/>selection</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.providerBinding",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    ffmpegbaseline [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">FFmpeg/libass Baseline</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Production rendering baseline</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.ffmpegBaseline",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    providerbinding -> ffmpegbaseline [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes PRODUCTION</FONT></TD></TR></TABLE>>,
        likec4_id=q47oy6,
        minlen=1,
        style=dashed,
        weight=2];
    opencue [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">OpenCue</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">ExecutionEnvironment only — NOT a Provider</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.opencue",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    providerbinding -> opencue [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes ExecutionEnv only</FONT></TD></TR></TABLE>>,
        likec4_id="1yjkeoj",
        minlen=1,
        style=dashed];
    remotion [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Remotion</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Non-production/POC subtitle template provider</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.remotion",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    providerbinding -> remotion [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes POC only</FONT></TD></TR></TABLE>>,
        likec4_id="3xlixp",
        minlen=1,
        style=dashed];
}
`;case`previewRenderJobApiFlow`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=previewRenderJobApiFlow,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        label="\\N",
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    previewrenderjobservice [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Preview Render Job Service</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">VS.1: Preview render job lifecycle</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.previewRenderJobService",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    providerbinding [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Provider Binding</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Deterministic eligibility + priority provider<BR/>selection</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.providerBinding",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    previewrenderjobservice -> providerbinding [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">compiles plan</FONT></TD></TR></TABLE>>,
        likec4_id=c8vxsg,
        style=dashed];
    ffmpegbaseline [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">FFmpeg/libass Baseline</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Production rendering baseline</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.ffmpegBaseline",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    previewrenderjobservice -> ffmpegbaseline [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">executes preview</FONT></TD></TR></TABLE>>,
        likec4_id=w0x3as,
        style=dashed];
    productruntime [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Product Runtime</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Product lifecycle management</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.productRuntime",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    previewrenderjobservice -> productruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">creates product</FONT></TD></TR></TABLE>>,
        likec4_id="1jv1scn",
        style=dashed];
    providerbinding -> ffmpegbaseline [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes PRODUCTION</FONT></TD></TR></TABLE>>,
        likec4_id=q47oy6,
        style=dashed];
    ffmpegbaseline -> productruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">produces output</FONT></TD></TR></TABLE>>,
        likec4_id="4joqk9",
        style=dashed];
    storageruntime [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage Runtime</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Storage/materialization management</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.storageRuntime",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    productruntime -> storageruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">manages lifecycle</FONT></TD></TR></TABLE>>,
        likec4_id="1h6i8bc",
        style=dashed,
        weight=3];
    storage [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Object storage / shared filesystem</FONT></TD></TR></TABLE>>,
        likec4_id=storage,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    storageruntime -> storage [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">persists artifacts</FONT></TD></TR></TABLE>>,
        likec4_id="1c6zqd9",
        minlen=1,
        style=dashed];
}
`;case`productArtifactResponseFlow`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=productArtifactResponseFlow,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    previewartifactqueryservice [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Preview Artifact Query Service</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">VS.1: Product/artifact metadata query</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.previewArtifactQueryService",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    productruntime [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Product Runtime</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Product lifecycle management</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.productRuntime",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    previewartifactqueryservice -> productruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">queries product</FONT></TD></TR></TABLE>>,
        likec4_id="1rm1ffi",
        style=dashed];
    storageruntime [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage Runtime</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Storage/materialization management</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.storageRuntime",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    previewartifactqueryservice -> storageruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">queries storage</FONT></TD></TR></TABLE>>,
        likec4_id=at7efg,
        style=dashed,
        weight=3];
    productruntime -> storageruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">manages lifecycle</FONT></TD></TR></TABLE>>,
        likec4_id="1h6i8bc",
        style=dashed,
        weight=3];
    storage [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Object storage / shared filesystem</FONT></TD></TR></TABLE>>,
        likec4_id=storage,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    storageruntime -> storage [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">persists artifacts</FONT></TD></TR></TABLE>>,
        likec4_id="1c6zqd9",
        minlen=1,
        style=dashed];
}
`;case`headlessApiValidationFlow`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=headlessApiValidationFlow,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    faketestlayer [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Fake Test Layer</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">VS.1-TEST.1: Fake implementations for<BR/>integration testing</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.fakeTestLayer",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    previewrenderjobservice [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Preview Render Job Service</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">VS.1: Preview render job lifecycle</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.previewRenderJobService",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    faketestlayer -> previewrenderjobservice [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">tests</FONT></TD></TR></TABLE>>,
        likec4_id="1xkwqek",
        minlen=1,
        style=dashed];
    previewartifactqueryservice [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Preview Artifact Query Service</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">VS.1: Product/artifact metadata query</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.previewArtifactQueryService",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    faketestlayer -> previewartifactqueryservice [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">tests</FONT></TD></TR></TABLE>>,
        likec4_id="1p2jn05",
        minlen=1,
        style=dashed];
}
`;case`storageDeliveryProfileArchitecture`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=storageDeliveryProfileArchitecture,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    subgraph cluster_storagemodule {
        graph [color="#1b3d88",
            fillcolor="#194b9e",
            label=<<FONT POINT-SIZE="11" COLOR="#bfdbfeb3"><B>STORAGE-MODULE</B></FONT>>,
            likec4_depth=1,
            likec4_id="mediaPlatform.storageModule",
            likec4_level=0,
            margin=40,
            style=filled
        ];
        storagedeliveryprofilevalidator [group="mediaPlatform.storageModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">StorageDeliveryProfileValidator</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Local validation: access mode, capability,<BR/>security rules</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.storageModule.storageDeliveryProfileValidator",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        storagedeliveryprofileconfig [group="mediaPlatform.storageModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">StorageDeliveryProfileConfig</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Spring config binding: storage.delivery.*</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.storageModule.storageDeliveryProfileConfig",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        storagedeliveryprofileregistry [group="mediaPlatform.storageModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">StorageDeliveryProfileRegistry</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Read-only registry: 8 canonical profiles, not<BR/>used for provider selection</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.storageModule.storageDeliveryProfileRegistry",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        storagedeliveryprofile [group="mediaPlatform.storageModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage Delivery Profile</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Profile contract: 8 canonical profiles,<BR/>access modes, capabilities</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.storageModule.storageDeliveryProfile",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        storagedeliveryprofiledto [group="mediaPlatform.storageModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">StorageDeliveryProfile DTO</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Internal value objects: Profile,<BR/>Capabilities, SecurityPolicy</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.storageModule.storageDeliveryProfileDTO",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
    }
    storagedeliveryprofilevalidator -> storagedeliveryprofile [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">validates</FONT></TD></TR></TABLE>>,
        likec4_id=b4ymtw,
        minlen=1,
        style=dashed];
    storagedeliveryprofileconfig -> storagedeliveryprofile [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">binds config</FONT></TD></TR></TABLE>>,
        likec4_id="1s6w44g",
        minlen=1,
        style=dashed];
    storagedeliveryprofileregistry -> storagedeliveryprofile [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">holds profiles</FONT></TD></TR></TABLE>>,
        likec4_id="1yt3l5r",
        minlen=1,
        style=dashed];
    storagedeliveryprofile -> storagedeliveryprofiledto [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">maps to DTOs</FONT></TD></TR></TABLE>>,
        likec4_id="1o16ljp",
        minlen=1,
        style=dashed];
}
`;case`ingestPreflightPolicyFlow`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=ingestPreflightPolicyFlow,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    subgraph cluster_ingestmodule {
        graph [color="#1b3d88",
            fillcolor="#194b9e",
            label=<<FONT POINT-SIZE="11" COLOR="#bfdbfeb3"><B>INGEST-MODULE</B></FONT>>,
            likec4_depth=1,
            likec4_id="mediaPlatform.ingestModule",
            likec4_level=0,
            margin=40,
            style=filled
        ];
        uploadhook [group="mediaPlatform.ingestModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">UploadReportOnlyPreflightHook</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Report-only hook: disabled by default,<BR/>fail-open, never rejects</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.ingestModule.uploadHook",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        tikaprovider [group="mediaPlatform.ingestModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">TikaDetectorProvider</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">MIME detection, extension mismatch,<BR/>content-type detection</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.ingestModule.tikaProvider",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        ffprobeprovider [group="mediaPlatform.ingestModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">FFprobeMetadataProvider</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Media technical metadata: duration, codec,<BR/>resolution</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.ingestModule.ffprobeProvider",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        metadatamerger [group="mediaPlatform.ingestModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">IngestMetadataMerger</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Merges Tika + FFprobe into unified result</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.ingestModule.metadataMerger",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        safereportdto [group="mediaPlatform.ingestModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">SafePreflightReportSummary</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Safe internal DTO: no raw metadata, no<BR/>storage internals</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.ingestModule.safeReportDTO",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        policyevaluator [group="mediaPlatform.ingestModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">ReportOnlyPreflightPolicyEvaluator</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Report-only evaluator: never rejects, fails<BR/>open</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.ingestModule.policyEvaluator",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        policyresult [group="mediaPlatform.ingestModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">PreflightPolicyEvaluationResult</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Policy result: ACCEPT, ACCEPT_WITH_WARNINGS,<BR/>REJECT_CANDIDATE</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.ingestModule.policyResult",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
    }
    uploadhook -> tikaprovider [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">detects MIME</FONT></TD></TR></TABLE>>,
        likec4_id="1mrq7uk",
        minlen=1,
        style=dashed];
    uploadhook -> ffprobeprovider [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">probes media</FONT></TD></TR></TABLE>>,
        likec4_id=njrk81,
        minlen=1,
        style=dashed];
    uploadhook -> metadatamerger [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">merges results</FONT></TD></TR></TABLE>>,
        likec4_id=gai0i5,
        style=dashed];
    metadatamerger -> safereportdto [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">produces safe report</FONT></TD></TR></TABLE>>,
        likec4_id="1m4tmfh",
        style=dashed];
    safereportdto -> policyevaluator [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">evaluates policy</FONT></TD></TR></TABLE>>,
        likec4_id="1xvjv8p",
        style=dashed];
    policyevaluator -> policyresult [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">produces result</FONT></TD></TR></TABLE>>,
        likec4_id=iy17s0,
        minlen=1,
        style=dashed];
}
`;case`r2ArtifactAccessPath`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=r2ArtifactAccessPath,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    s3materializer [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">S3ObjectMaterializer</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">S3/R2-compatible storage materializer</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.storageModule.s3Materializer",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    accessdescriptor [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">AccessDescriptor</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">User-facing access contract: SIGNED_URL<BR/>generated on demand</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.storageModule.accessDescriptor",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    s3materializer -> accessdescriptor [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">generates signed URL</FONT></TD></TR></TABLE>>,
        likec4_id="1yel4aa",
        minlen=1,
        style=dashed,
        weight=3];
    storage [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Object storage / shared filesystem</FONT></TD></TR></TABLE>>,
        likec4_id=storage,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    s3materializer -> storage [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">R2/S3-compatible</FONT></TD></TR></TABLE>>,
        likec4_id="1bgwg9w",
        style=dashed];
    storageruntime [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage Runtime</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Storage/materialization management</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.storageRuntime",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    storageruntime -> storage [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">persists artifacts</FONT></TD></TR></TABLE>>,
        likec4_id="1c6zqd9",
        minlen=1,
        style=dashed];
}
`;default:throw Error(`Unknown viewId: `+e)}},t=e=>{switch(e){case`index`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="1425pt" height="1178pt"
 viewBox="0.00 0.00 1425.00 1178.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 1163.45)">
<!-- user -->
<g id="node1" class="node">
<title>user</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="535.04,-1148.4 215,-1148.4 215,-968.4 535.04,-968.4 535.04,-1148.4"/>
<text xml:space="preserve" text-anchor="start" x="353.91" y="-1061.4" font-family="Arial" font-size="20.00" fill="#eff6ff">User</text>
<text xml:space="preserve" text-anchor="start" x="330.42" y="-1038.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">Platform user</text>
</g>
<!-- mediaplatform -->
<g id="node2" class="node">
<title>mediaplatform</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="535.04,-502.8 215,-502.8 215,-322.8 535.04,-322.8 535.04,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="308.33" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">media&#45;platform</text>
<text xml:space="preserve" text-anchor="start" x="321.25" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Render platform</text>
</g>
<!-- reviewer -->
<g id="node3" class="node">
<title>reviewer</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="965.04,-1148.4 645,-1148.4 645,-968.4 965.04,-968.4 965.04,-1148.4"/>
<text xml:space="preserve" text-anchor="start" x="728.33" y="-1061.4" font-family="Arial" font-size="20.00" fill="#eff6ff">Human Reviewer</text>
<text xml:space="preserve" text-anchor="start" x="765.42" y="-1038.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">Final arbiter</text>
</g>
<!-- hermes -->
<g id="node4" class="node">
<title>hermes</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="965.04,-825.6 645,-825.6 645,-645.6 965.04,-645.6 965.04,-825.6"/>
<text xml:space="preserve" text-anchor="start" x="706.65" y="-738.6" font-family="Arial" font-size="20.00" fill="#eff6ff">Hermes Control Plane</text>
<text xml:space="preserve" text-anchor="start" x="722.48" y="-715.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Multi&#45;agent orchestration</text>
</g>
<!-- cloudflare -->
<g id="node5" class="node">
<title>cloudflare</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="965.04,-502.8 645,-502.8 645,-322.8 965.04,-322.8 965.04,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="759.44" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Cloudflare</text>
<text xml:space="preserve" text-anchor="start" x="727.06" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">R2, Pages, AI Gateway</text>
</g>
<!-- telegram -->
<g id="node6" class="node">
<title>telegram</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1395.04,-502.8 1075,-502.8 1075,-322.8 1395.04,-322.8 1395.04,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="1192.78" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Telegram</text>
<text xml:space="preserve" text-anchor="start" x="1194.17" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Notifications</text>
</g>
<!-- aiproviders -->
<g id="node7" class="node">
<title>aiproviders</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-180 0,-180 0,0 320.04,0 320.04,-180"/>
<text xml:space="preserve" text-anchor="start" x="105.56" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">AI Providers</text>
<text xml:space="preserve" text-anchor="start" x="47.88" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">MiMo, LongCat, OpenRouter, etc.</text>
</g>
<!-- storage -->
<g id="node8" class="node">
<title>storage</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="750.04,-180 430,-180 430,0 750.04,0 750.04,-180"/>
<text xml:space="preserve" text-anchor="start" x="555" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage</text>
<text xml:space="preserve" text-anchor="start" x="476.64" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Object storage / shared filesystem</text>
</g>
<!-- user&#45;&gt;mediaplatform -->
<g id="edge1" class="edge">
<title>user&#45;&gt;mediaplatform</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M375.02,-968.59C375.02,-849.18 375.02,-637.53 375.02,-513.03"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="377.65,-513.26 375.02,-505.76 372.4,-513.26 377.65,-513.26"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="375.02,-724.2 375.02,-747 410.59,-747 410.59,-724.2 375.02,-724.2"/>
<text xml:space="preserve" text-anchor="start" x="378.02" y="-730" font-family="Arial" font-size="14.00" fill="#c9c9c9">uses</text>
</g>
<!-- mediaplatform&#45;&gt;aiproviders -->
<g id="edge6" class="edge">
<title>mediaplatform&#45;&gt;aiproviders</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M315.42,-322.87C287.45,-281.14 254.06,-231.31 225.4,-188.56"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="227.59,-187.11 221.24,-182.34 223.23,-190.04 227.59,-187.11"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="273.97,-240 273.97,-262.8 356.99,-262.8 356.99,-240 273.97,-240"/>
<text xml:space="preserve" text-anchor="start" x="276.97" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">calls models</text>
</g>
<!-- mediaplatform&#45;&gt;storage -->
<g id="edge7" class="edge">
<title>mediaplatform&#45;&gt;storage</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M434.62,-322.87C462.59,-281.14 495.98,-231.31 524.64,-188.56"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="526.81,-190.04 528.8,-182.34 522.45,-187.11 526.81,-190.04"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="488.97,-240 488.97,-262.8 570.44,-262.8 570.44,-240 488.97,-240"/>
<text xml:space="preserve" text-anchor="start" x="491.97" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">reads/writes</text>
</g>
<!-- reviewer&#45;&gt;hermes -->
<g id="edge2" class="edge">
<title>reviewer&#45;&gt;hermes</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M805.02,-968.47C805.02,-927.27 805.02,-878.16 805.02,-835.77"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="807.65,-835.96 805.02,-828.46 802.4,-835.96 807.65,-835.96"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="805.02,-885.6 805.02,-908.4 858.48,-908.4 858.48,-885.6 805.02,-885.6"/>
<text xml:space="preserve" text-anchor="start" x="808.02" y="-891.4" font-family="Arial" font-size="14.00" fill="#c9c9c9">reviews</text>
</g>
<!-- hermes&#45;&gt;mediaplatform -->
<g id="edge5" class="edge">
<title>hermes&#45;&gt;mediaplatform</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M685.81,-645.67C628.72,-603.07 560.3,-552.03 502.21,-508.69"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="504.09,-506.82 496.51,-504.44 500.95,-511.03 504.09,-506.82"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="602.91,-562.8 602.91,-585.6 664.95,-585.6 664.95,-562.8 602.91,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="605.91" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">develops</text>
</g>
<!-- hermes&#45;&gt;cloudflare -->
<g id="edge3" class="edge">
<title>hermes&#45;&gt;cloudflare</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M805.02,-645.67C805.02,-604.47 805.02,-555.36 805.02,-512.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="807.65,-513.16 805.02,-505.66 802.4,-513.16 807.65,-513.16"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="805.02,-562.8 805.02,-585.6 870.17,-585.6 870.17,-562.8 805.02,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="808.02" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">publishes</text>
</g>
<!-- hermes&#45;&gt;telegram -->
<g id="edge4" class="edge">
<title>hermes&#45;&gt;telegram</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M924.23,-645.67C981.32,-603.07 1049.74,-552.03 1107.83,-508.69"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1109.09,-511.03 1113.53,-504.44 1105.95,-506.82 1109.09,-511.03"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1032.91,-562.8 1032.91,-585.6 1083.27,-585.6 1083.27,-562.8 1032.91,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="1035.91" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">notifies</text>
</g>
</g>
</svg>
`;case`systemContext`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="478pt" height="627pt"
 viewBox="0.00 0.00 478.00 627.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 612.25)">
<g id="clust1" class="cluster">
<title>cluster_mediaplatform</title>
<polygon fill="#194b9e" stroke="#1b3d88" points="8,-8 8,-589.2 440,-589.2 440,-8 8,-8"/>
<text xml:space="preserve" text-anchor="start" x="16" y="-576.3" font-family="Arial" font-weight="bold" font-size="11.00" fill="#bfdbfe" fill-opacity="0.701961">MEDIA&#45;PLATFORM</text>
</g>
<!-- opencue -->
<g id="node1" class="node">
<title>opencue</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="397.45,-528 50.55,-528 50.55,-348 397.45,-348 397.45,-528"/>
<text xml:space="preserve" text-anchor="start" x="181.19" y="-441" font-family="Arial" font-size="20.00" fill="#eff6ff">OpenCue</text>
<text xml:space="preserve" text-anchor="start" x="70.6" y="-418" font-family="Arial" font-size="15.00" fill="#bfdbfe">ExecutionEnvironment only — NOT a Provider</text>
</g>
<!-- remotion -->
<g id="node2" class="node">
<title>remotion</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="399.55,-228 48.45,-228 48.45,-48 399.55,-48 399.55,-228"/>
<text xml:space="preserve" text-anchor="start" x="181.2" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">Remotion</text>
<text xml:space="preserve" text-anchor="start" x="68.51" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">Non&#45;production/POC subtitle template provider</text>
</g>
<!-- opencue&#45;&gt;remotion -->
</g>
</svg>
`;case`containerDiagram`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="2021pt" height="973pt"
 viewBox="0.00 0.00 2021.00 973.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 957.85)">
<g id="clust1" class="cluster">
<title>cluster_mediaplatform</title>
<polygon fill="#194b9e" stroke="#1b3d88" points="8,-8 8,-934.8 1567,-934.8 1567,-8 8,-8"/>
<text xml:space="preserve" text-anchor="start" x="16" y="-921.9" font-family="Arial" font-weight="bold" font-size="11.00" fill="#bfdbfe" fill-opacity="0.701961">MEDIA&#45;PLATFORM</text>
</g>
<!-- platformapp -->
<g id="node1" class="node">
<title>platformapp</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1097.02,-873.6 776.98,-873.6 776.98,-693.6 1097.02,-693.6 1097.02,-873.6"/>
<text xml:space="preserve" text-anchor="start" x="880.86" y="-786.6" font-family="Arial" font-size="20.00" fill="#eff6ff">platform&#45;app</text>
<text xml:space="preserve" text-anchor="start" x="860.7" y="-763.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Spring Boot entry point</text>
</g>
<!-- aimodule -->
<g id="node2" class="node">
<title>aimodule</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-873.6 47.98,-873.6 47.98,-693.6 368.02,-693.6 368.02,-873.6"/>
<text xml:space="preserve" text-anchor="start" x="164.09" y="-786.6" font-family="Arial" font-size="20.00" fill="#eff6ff">ai&#45;module</text>
<text xml:space="preserve" text-anchor="start" x="163.81" y="-763.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">AI integration</text>
</g>
<!-- rendermodule -->
<g id="node3" class="node">
<title>rendermodule</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1527.02,-550.8 1206.98,-550.8 1206.98,-370.8 1527.02,-370.8 1527.02,-550.8"/>
<text xml:space="preserve" text-anchor="start" x="1301.97" y="-463.8" font-family="Arial" font-size="20.00" fill="#eff6ff">render&#45;module</text>
<text xml:space="preserve" text-anchor="start" x="1300.3" y="-440.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Core render domain</text>
</g>
<!-- storagemodule -->
<g id="node4" class="node">
<title>storagemodule</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1097.02,-550.8 776.98,-550.8 776.98,-370.8 1097.02,-370.8 1097.02,-550.8"/>
<text xml:space="preserve" text-anchor="start" x="867.52" y="-463.8" font-family="Arial" font-size="20.00" fill="#eff6ff">storage&#45;module</text>
<text xml:space="preserve" text-anchor="start" x="817.77" y="-440.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Storage delivery and materialization</text>
</g>
<!-- ingestmodule -->
<g id="node5" class="node">
<title>ingestmodule</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="667.02,-550.8 346.98,-550.8 346.98,-370.8 667.02,-370.8 667.02,-550.8"/>
<text xml:space="preserve" text-anchor="start" x="444.19" y="-463.8" font-family="Arial" font-size="20.00" fill="#eff6ff">ingest&#45;module</text>
<text xml:space="preserve" text-anchor="start" x="373.99" y="-440.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Upload preflight and metadata detection</text>
</g>
<!-- opencue -->
<g id="node6" class="node">
<title>opencue</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1527.45,-228 1180.55,-228 1180.55,-48 1527.45,-48 1527.45,-228"/>
<text xml:space="preserve" text-anchor="start" x="1311.19" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">OpenCue</text>
<text xml:space="preserve" text-anchor="start" x="1200.6" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">ExecutionEnvironment only — NOT a Provider</text>
</g>
<!-- remotion -->
<g id="node7" class="node">
<title>remotion</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1070.55,-228 719.45,-228 719.45,-48 1070.55,-48 1070.55,-228"/>
<text xml:space="preserve" text-anchor="start" x="852.2" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">Remotion</text>
<text xml:space="preserve" text-anchor="start" x="739.51" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">Non&#45;production/POC subtitle template provider</text>
</g>
<!-- sharedkernel -->
<g id="node8" class="node">
<title>sharedkernel</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="609.02,-228 288.98,-228 288.98,-48 609.02,-48 609.02,-228"/>
<text xml:space="preserve" text-anchor="start" x="387.86" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">shared&#45;kernel</text>
<text xml:space="preserve" text-anchor="start" x="364.38" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">Shared domain primitives</text>
</g>
<!-- storage -->
<g id="node9" class="node">
<title>storage</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1991.02,-228 1670.98,-228 1670.98,-48 1991.02,-48 1991.02,-228"/>
<text xml:space="preserve" text-anchor="start" x="1795.98" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage</text>
<text xml:space="preserve" text-anchor="start" x="1717.62" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">Object storage / shared filesystem</text>
</g>
<!-- platformapp&#45;&gt;rendermodule -->
<g id="edge1" class="edge">
<title>platformapp&#45;&gt;rendermodule</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1056.21,-693.67C1113.3,-651.07 1181.72,-600.03 1239.81,-556.69"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1241.07,-559.03 1245.51,-552.44 1237.93,-554.82 1241.07,-559.03"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1164.89,-610.8 1164.89,-633.6 1200.47,-633.6 1200.47,-610.8 1164.89,-610.8"/>
<text xml:space="preserve" text-anchor="start" x="1167.89" y="-616.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">uses</text>
</g>
<!-- platformapp&#45;&gt;storagemodule -->
<g id="edge2" class="edge">
<title>platformapp&#45;&gt;storagemodule</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M937,-693.67C937,-652.47 937,-603.36 937,-560.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="939.63,-561.16 937,-553.66 934.38,-561.16 939.63,-561.16"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="937,-610.8 937,-633.6 972.57,-633.6 972.57,-610.8 937,-610.8"/>
<text xml:space="preserve" text-anchor="start" x="940" y="-616.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">uses</text>
</g>
<!-- platformapp&#45;&gt;ingestmodule -->
<g id="edge3" class="edge">
<title>platformapp&#45;&gt;ingestmodule</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M817.79,-693.67C760.7,-651.07 692.28,-600.03 634.19,-556.69"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="636.07,-554.82 628.49,-552.44 632.93,-559.03 636.07,-554.82"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="734.89,-610.8 734.89,-633.6 770.47,-633.6 770.47,-610.8 734.89,-610.8"/>
<text xml:space="preserve" text-anchor="start" x="737.89" y="-616.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">uses</text>
</g>
<!-- aimodule&#45;&gt;sharedkernel -->
<g id="edge4" class="edge">
<title>aimodule&#45;&gt;sharedkernel</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M190.15,-693.85C177.07,-607.64 168.42,-474.87 212.82,-370.8 235.11,-318.56 275.39,-272.11 316.46,-234.76"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="318.04,-236.87 321.88,-229.91 314.54,-232.95 318.04,-236.87"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="212.82,-449.4 212.82,-472.2 292,-472.2 292,-449.4 212.82,-449.4"/>
<text xml:space="preserve" text-anchor="start" x="215.82" y="-455.2" font-family="Arial" font-size="14.00" fill="#c9c9c9">depends on</text>
</g>
<!-- rendermodule&#45;&gt;opencue -->
<g id="edge5" class="edge">
<title>rendermodule&#45;&gt;opencue</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1342.62,-370.97C1338.21,-351.3 1334.3,-330.46 1332.02,-310.8 1329.28,-287.15 1330.34,-261.69 1333.11,-237.8"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1335.67,-238.45 1334.01,-230.68 1330.47,-237.79 1335.67,-238.45"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1332.02,-288 1332.02,-310.8 1496,-310.8 1496,-288 1332.02,-288"/>
<text xml:space="preserve" text-anchor="start" x="1335.02" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes ExecutionEnv only</text>
</g>
<!-- rendermodule&#45;&gt;remotion -->
<g id="edge6" class="edge">
<title>rendermodule&#45;&gt;remotion</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1236.15,-370.87C1173.35,-328.19 1098.07,-277.02 1034.23,-233.63"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1035.88,-231.58 1028.2,-229.53 1032.93,-235.92 1035.88,-231.58"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1145.15,-288 1145.15,-310.8 1253.86,-310.8 1253.86,-288 1145.15,-288"/>
<text xml:space="preserve" text-anchor="start" x="1148.15" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes POC only</text>
</g>
<!-- rendermodule&#45;&gt;sharedkernel -->
<g id="edge8" class="edge">
<title>rendermodule&#45;&gt;sharedkernel</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1207.18,-391.42C1188.69,-384.17 1170,-377.14 1152,-370.8 938.85,-295.74 877.15,-303.06 664,-228 649.09,-222.75 633.72,-217.03 618.37,-211.09"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="619.58,-208.75 611.64,-208.47 617.67,-213.64 619.58,-208.75"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="949.53,-288 949.53,-310.8 1028.71,-310.8 1028.71,-288 949.53,-288"/>
<text xml:space="preserve" text-anchor="start" x="952.53" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">depends on</text>
</g>
<!-- rendermodule&#45;&gt;storage -->
<g id="edge7" class="edge">
<title>rendermodule&#45;&gt;storage</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1526.8,-427.15C1602.28,-404.94 1689.15,-368.72 1751,-310.8 1772.48,-290.68 1788.72,-263.65 1800.76,-236.99"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1803.05,-238.29 1803.63,-230.36 1798.24,-236.19 1803.05,-238.29"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1768.18,-288 1768.18,-310.8 1875.32,-310.8 1875.32,-288 1768.18,-288"/>
<text xml:space="preserve" text-anchor="start" x="1771.18" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">persists artifacts</text>
</g>
<!-- storagemodule&#45;&gt;sharedkernel -->
<g id="edge10" class="edge">
<title>storagemodule&#45;&gt;sharedkernel</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M779.51,-370.9C747.15,-351.69 713.59,-331.04 682.82,-310.8 646.59,-286.96 608.28,-259.73 573.39,-234.07"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="575.1,-232.07 567.5,-229.73 571.98,-236.3 575.1,-232.07"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="682.82,-288 682.82,-310.8 762,-310.8 762,-288 682.82,-288"/>
<text xml:space="preserve" text-anchor="start" x="685.82" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">depends on</text>
</g>
<!-- storagemodule&#45;&gt;storage -->
<g id="edge9" class="edge">
<title>storagemodule&#45;&gt;storage</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1097,-388.87C1115.33,-382.15 1133.93,-375.94 1152,-370.8 1310.96,-325.56 1362.79,-364.78 1519,-310.8 1575.51,-291.27 1633.85,-261.99 1684.56,-233.1"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1685.79,-235.42 1690.99,-229.41 1683.18,-230.87 1685.79,-235.42"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1573.11,-288 1573.11,-310.8 1690.39,-310.8 1690.39,-288 1573.11,-288"/>
<text xml:space="preserve" text-anchor="start" x="1576.11" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">R2/S3&#45;compatible</text>
</g>
<!-- ingestmodule&#45;&gt;sharedkernel -->
<g id="edge11" class="edge">
<title>ingestmodule&#45;&gt;sharedkernel</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M459.6,-370.88C451.41,-351.58 444.15,-330.91 439.82,-310.8 434.84,-287.65 433.83,-262.37 434.8,-238.49"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="437.42,-238.64 435.2,-231.01 432.18,-238.36 437.42,-238.64"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="439.82,-288 439.82,-310.8 519,-310.8 519,-288 439.82,-288"/>
<text xml:space="preserve" text-anchor="start" x="442.82" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">depends on</text>
</g>
</g>
</svg>
`;case`controlPlane`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="1339pt" height="627pt"
 viewBox="0.00 0.00 1339.00 627.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 612.25)">
<g id="clust1" class="cluster">
<title>cluster_hermes</title>
<polygon fill="#194b9e" stroke="#1b3d88" points="8,-8 8,-589.2 1301,-589.2 1301,-8 8,-8"/>
<text xml:space="preserve" text-anchor="start" x="16" y="-576.3" font-family="Arial" font-weight="bold" font-size="11.00" fill="#bfdbfe" fill-opacity="0.701961">HERMES CONTROL PLANE</text>
</g>
<!-- hermesagent -->
<g id="node1" class="node">
<title>hermesagent</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-528 47.98,-528 47.98,-348 368.02,-348 368.02,-528"/>
<text xml:space="preserve" text-anchor="start" x="144.08" y="-450" font-family="Arial" font-size="20.00" fill="#eff6ff">Hermes Agent</text>
<text xml:space="preserve" text-anchor="start" x="77.94" y="-427" font-family="Arial" font-size="15.00" fill="#bfdbfe">Gateway, orchestrator, Level 3 Feature</text>
<text xml:space="preserve" text-anchor="start" x="168.81" y="-409" font-family="Arial" font-size="15.00" fill="#bfdbfe">Coordinator</text>
</g>
<!-- codingagents -->
<g id="node2" class="node">
<title>codingagents</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="815.9,-528 478.1,-528 478.1,-348 815.9,-348 815.9,-528"/>
<text xml:space="preserve" text-anchor="start" x="581.4" y="-450" font-family="Arial" font-size="20.00" fill="#eff6ff">Coding Agents</text>
<text xml:space="preserve" text-anchor="start" x="498.16" y="-427" font-family="Arial" font-size="15.00" fill="#bfdbfe">OpenCode, Codex, Kilo Code, Claude Code,</text>
<text xml:space="preserve" text-anchor="start" x="629.49" y="-409" font-family="Arial" font-size="15.00" fill="#bfdbfe">Aider</text>
</g>
<!-- reviewinfra -->
<g id="node3" class="node">
<title>reviewinfra</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1260.6,-528 925.4,-528 925.4,-348 1260.6,-348 1260.6,-528"/>
<text xml:space="preserve" text-anchor="start" x="998.52" y="-441" font-family="Arial" font-size="20.00" fill="#eff6ff">Review Infrastructure</text>
<text xml:space="preserve" text-anchor="start" x="945.46" y="-418" font-family="Arial" font-size="15.00" fill="#bfdbfe">CODEOWNERS, Semgrep, Review Packets</text>
</g>
<!-- policies -->
<g id="node4" class="node">
<title>policies</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-228 47.98,-228 47.98,-48 368.02,-48 368.02,-228"/>
<text xml:space="preserve" text-anchor="start" x="173.54" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">Policies</text>
<text xml:space="preserve" text-anchor="start" x="112.96" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">Permissions, stop conditions</text>
</g>
<!-- dashboard -->
<g id="node5" class="node">
<title>dashboard</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1253.02,-228 932.98,-228 932.98,-48 1253.02,-48 1253.02,-228"/>
<text xml:space="preserve" text-anchor="start" x="1044.08" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">Dashboard</text>
<text xml:space="preserve" text-anchor="start" x="1053.41" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">scribe.cc.cd</text>
</g>
<!-- hermesagent&#45;&gt;policies -->
<!-- codingagents&#45;&gt;reviewinfra -->
<!-- reviewinfra&#45;&gt;dashboard -->
</g>
</svg>
`;case`controlPlaneContext`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="2010pt" height="1122pt"
 viewBox="0.00 0.00 2010.00 1122.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 1107.25)">
<g id="clust1" class="cluster">
<title>cluster_hermes</title>
<polygon fill="#194b9e" stroke="#1b3d88" points="8,-261 8,-843.2 1411,-843.2 1411,-261 8,-261"/>
<text xml:space="preserve" text-anchor="start" x="16" y="-830.3" font-family="Arial" font-weight="bold" font-size="11.00" fill="#bfdbfe" fill-opacity="0.701961">HERMES CONTROL PLANE</text>
</g>
<!-- hermesagent -->
<g id="node1" class="node">
<title>hermesagent</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-782 47.98,-782 47.98,-602 368.02,-602 368.02,-782"/>
<text xml:space="preserve" text-anchor="start" x="144.08" y="-704" font-family="Arial" font-size="20.00" fill="#eff6ff">Hermes Agent</text>
<text xml:space="preserve" text-anchor="start" x="77.94" y="-681" font-family="Arial" font-size="15.00" fill="#bfdbfe">Gateway, orchestrator, Level 3 Feature</text>
<text xml:space="preserve" text-anchor="start" x="168.81" y="-663" font-family="Arial" font-size="15.00" fill="#bfdbfe">Coordinator</text>
</g>
<!-- codingagents -->
<g id="node2" class="node">
<title>codingagents</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="815.9,-782 478.1,-782 478.1,-602 815.9,-602 815.9,-782"/>
<text xml:space="preserve" text-anchor="start" x="581.4" y="-704" font-family="Arial" font-size="20.00" fill="#eff6ff">Coding Agents</text>
<text xml:space="preserve" text-anchor="start" x="498.16" y="-681" font-family="Arial" font-size="15.00" fill="#bfdbfe">OpenCode, Codex, Kilo Code, Claude Code,</text>
<text xml:space="preserve" text-anchor="start" x="629.49" y="-663" font-family="Arial" font-size="15.00" fill="#bfdbfe">Aider</text>
</g>
<!-- reviewinfra -->
<g id="node3" class="node">
<title>reviewinfra</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1370.6,-782 1035.4,-782 1035.4,-602 1370.6,-602 1370.6,-782"/>
<text xml:space="preserve" text-anchor="start" x="1108.52" y="-695" font-family="Arial" font-size="20.00" fill="#eff6ff">Review Infrastructure</text>
<text xml:space="preserve" text-anchor="start" x="1055.46" y="-672" font-family="Arial" font-size="15.00" fill="#bfdbfe">CODEOWNERS, Semgrep, Review Packets</text>
</g>
<!-- policies -->
<g id="node4" class="node">
<title>policies</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-481 47.98,-481 47.98,-301 368.02,-301 368.02,-481"/>
<text xml:space="preserve" text-anchor="start" x="173.54" y="-394" font-family="Arial" font-size="20.00" fill="#eff6ff">Policies</text>
<text xml:space="preserve" text-anchor="start" x="112.96" y="-371" font-family="Arial" font-size="15.00" fill="#bfdbfe">Permissions, stop conditions</text>
</g>
<!-- dashboard -->
<g id="node5" class="node">
<title>dashboard</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1084.02,-481 763.98,-481 763.98,-301 1084.02,-301 1084.02,-481"/>
<text xml:space="preserve" text-anchor="start" x="875.08" y="-394" font-family="Arial" font-size="20.00" fill="#eff6ff">Dashboard</text>
<text xml:space="preserve" text-anchor="start" x="884.41" y="-371" font-family="Arial" font-size="15.00" fill="#bfdbfe">scribe.cc.cd</text>
</g>
<!-- cloudflare -->
<g id="node6" class="node">
<title>cloudflare</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="654.02,-180 333.98,-180 333.98,0 654.02,0 654.02,-180"/>
<text xml:space="preserve" text-anchor="start" x="448.42" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Cloudflare</text>
<text xml:space="preserve" text-anchor="start" x="416.04" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">R2, Pages, AI Gateway</text>
</g>
<!-- telegram -->
<g id="node7" class="node">
<title>telegram</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1084.02,-180 763.98,-180 763.98,0 1084.02,0 1084.02,-180"/>
<text xml:space="preserve" text-anchor="start" x="881.76" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Telegram</text>
<text xml:space="preserve" text-anchor="start" x="883.15" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Notifications</text>
</g>
<!-- mediaplatform -->
<g id="node8" class="node">
<title>mediaplatform</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1980.02,-180 1659.98,-180 1659.98,0 1980.02,0 1980.02,-180"/>
<text xml:space="preserve" text-anchor="start" x="1753.31" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">media&#45;platform</text>
<text xml:space="preserve" text-anchor="start" x="1766.23" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Render platform</text>
</g>
<!-- user -->
<g id="node9" class="node">
<title>user</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1514.02,-180 1193.98,-180 1193.98,0 1514.02,0 1514.02,-180"/>
<text xml:space="preserve" text-anchor="start" x="1332.89" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">User</text>
<text xml:space="preserve" text-anchor="start" x="1309.4" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Platform user</text>
</g>
<!-- reviewer -->
<g id="node10" class="node">
<title>reviewer</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-1092.2 47.98,-1092.2 47.98,-912.2 368.02,-912.2 368.02,-1092.2"/>
<text xml:space="preserve" text-anchor="start" x="131.31" y="-1005.2" font-family="Arial" font-size="20.00" fill="#eff6ff">Human Reviewer</text>
<text xml:space="preserve" text-anchor="start" x="168.4" y="-982.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">Final arbiter</text>
</g>
<!-- hermesagent&#45;&gt;policies -->
<!-- codingagents&#45;&gt;reviewinfra -->
<!-- dashboard&#45;&gt;cloudflare -->
<g id="edge3" class="edge">
<title>dashboard&#45;&gt;cloudflare</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M738.49,-261C702.11,-235.71 664.59,-209.62 630.19,-185.7"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="631.87,-183.67 624.21,-181.54 628.87,-187.98 631.87,-183.67"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="614.6,-220.16 614.6,-242.96 679.75,-242.96 679.75,-220.16 614.6,-220.16"/>
<text xml:space="preserve" text-anchor="start" x="617.6" y="-225.96" font-family="Arial" font-size="14.00" fill="#c9c9c9">publishes</text>
</g>
<!-- dashboard&#45;&gt;telegram -->
<g id="edge4" class="edge">
<title>dashboard&#45;&gt;telegram</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M924,-261C924,-237.31 924,-212.93 924,-190.28"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="926.63,-190.34 924,-182.84 921.38,-190.34 926.63,-190.34"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="873.64,-220.25 873.64,-243.05 924,-243.05 924,-220.25 873.64,-220.25"/>
<text xml:space="preserve" text-anchor="start" x="876.64" y="-226.05" font-family="Arial" font-size="14.00" fill="#c9c9c9">notifies</text>
</g>
<!-- dashboard&#45;&gt;mediaplatform -->
<g id="edge5" class="edge">
<title>dashboard&#45;&gt;mediaplatform</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1325.69,-261C1406.39,-234.59 1490.5,-206.69 1569,-180 1595.43,-171.01 1623.36,-161.31 1650.6,-151.74"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1651.15,-154.33 1657.35,-149.36 1649.41,-149.38 1651.15,-154.33"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1431.04,-205.61 1431.04,-228.41 1493.09,-228.41 1493.09,-205.61 1431.04,-205.61"/>
<text xml:space="preserve" text-anchor="start" x="1434.04" y="-211.41" font-family="Arial" font-size="14.00" fill="#c9c9c9">develops</text>
</g>
<!-- user&#45;&gt;mediaplatform -->
<g id="edge6" class="edge">
<title>user&#45;&gt;mediaplatform</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1513.72,-90C1557.59,-90 1605.33,-90 1649.77,-90"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1649.57,-92.63 1657.07,-90 1649.57,-87.38 1649.57,-92.63"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1569.21,-93 1569.21,-115.8 1604.79,-115.8 1604.79,-93 1569.21,-93"/>
<text xml:space="preserve" text-anchor="start" x="1572.21" y="-98.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">uses</text>
</g>
<!-- reviewer&#45;&gt;hermesagent -->
<g id="edge7" class="edge">
<title>reviewer&#45;&gt;hermesagent</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M208,-912.47C208,-893.89 208,-873.76 208,-853.48"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="210.63,-853.73 208,-846.23 205.38,-853.73 210.63,-853.73"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="154.54,-877.56 154.54,-900.36 208,-900.36 208,-877.56 154.54,-877.56"/>
<text xml:space="preserve" text-anchor="start" x="157.54" y="-883.36" font-family="Arial" font-size="14.00" fill="#c9c9c9">reviews</text>
</g>
</g>
</svg>
`;case`vs0VerticalSlice`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="1532pt" height="2539pt"
 viewBox="0.00 0.00 1532.00 2539.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 2523.85)">
<g id="clust1" class="cluster">
<title>cluster_rendermodule</title>
<polygon fill="#194b9e" stroke="#1b3d88" points="8,-282.8 8,-2500.8 1142,-2500.8 1142,-282.8 8,-282.8"/>
<text xml:space="preserve" text-anchor="start" x="16" y="-2487.9" font-family="Arial" font-weight="bold" font-size="11.00" fill="#bfdbfe" fill-opacity="0.701961">RENDER&#45;MODULE</text>
</g>
<!-- timelineedit -->
<g id="node1" class="node">
<title>timelineedit</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="406.73,-2439.6 57.27,-2439.6 57.27,-2259.6 406.73,-2259.6 406.73,-2439.6"/>
<text xml:space="preserve" text-anchor="start" x="125.3" y="-2361.6" font-family="Arial" font-size="20.00" fill="#eff6ff">Timeline Edit Command</text>
<text xml:space="preserve" text-anchor="start" x="77.33" y="-2338.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">TL.0: Sealed interface with 12 typed command</text>
<text xml:space="preserve" text-anchor="start" x="206.99" y="-2320.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">records</text>
</g>
<!-- faketestlayer -->
<g id="node2" class="node">
<title>faketestlayer</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1091.02,-2439.6 770.98,-2439.6 770.98,-2259.6 1091.02,-2259.6 1091.02,-2439.6"/>
<text xml:space="preserve" text-anchor="start" x="858.75" y="-2361.6" font-family="Arial" font-size="20.00" fill="#eff6ff">Fake Test Layer</text>
<text xml:space="preserve" text-anchor="start" x="798.45" y="-2338.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">VS.1&#45;TEST.1: Fake implementations for</text>
<text xml:space="preserve" text-anchor="start" x="871.79" y="-2320.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">integration testing</text>
</g>
<!-- captiontemplate -->
<g id="node3" class="node">
<title>captiontemplate</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="399.21,-2116.8 64.79,-2116.8 64.79,-1936.8 399.21,-1936.8 399.21,-2116.8"/>
<text xml:space="preserve" text-anchor="start" x="153.07" y="-2038.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Caption Template</text>
<text xml:space="preserve" text-anchor="start" x="84.84" y="-2015.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">CT.0: Typed intent model for caption/subtitle</text>
<text xml:space="preserve" text-anchor="start" x="200.31" y="-1997.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">rendering</text>
</g>
<!-- previewrenderjobservice -->
<g id="node4" class="node">
<title>previewrenderjobservice</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="846.02,-1794 525.98,-1794 525.98,-1614 846.02,-1614 846.02,-1794"/>
<text xml:space="preserve" text-anchor="start" x="559.83" y="-1707" font-family="Arial" font-size="20.00" fill="#eff6ff">Preview Render Job Service</text>
<text xml:space="preserve" text-anchor="start" x="574.28" y="-1684" font-family="Arial" font-size="15.00" fill="#bfdbfe">VS.1: Preview render job lifecycle</text>
</g>
<!-- previewartifactqueryservice -->
<g id="node5" class="node">
<title>previewartifactqueryservice</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1102.02,-1148.4 781.98,-1148.4 781.98,-968.4 1102.02,-968.4 1102.02,-1148.4"/>
<text xml:space="preserve" text-anchor="start" x="806.4" y="-1061.4" font-family="Arial" font-size="20.00" fill="#eff6ff">Preview Artifact Query Service</text>
<text xml:space="preserve" text-anchor="start" x="816.94" y="-1038.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">VS.1: Product/artifact metadata query</text>
</g>
<!-- assstylemapper -->
<g id="node6" class="node">
<title>assstylemapper</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="415.88,-1794 48.12,-1794 48.12,-1614 415.88,-1614 415.88,-1794"/>
<text xml:space="preserve" text-anchor="start" x="159.19" y="-1716" font-family="Arial" font-size="20.00" fill="#eff6ff">AssStyleMapper</text>
<text xml:space="preserve" text-anchor="start" x="68.18" y="-1693" font-family="Arial" font-size="15.00" fill="#bfdbfe">CT.0: Maps CaptionTemplateSpec to ASS format</text>
<text xml:space="preserve" text-anchor="start" x="194.07" y="-1675" font-family="Arial" font-size="15.00" fill="#bfdbfe">parameters</text>
</g>
<!-- providerbinding -->
<g id="node7" class="node">
<title>providerbinding</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-1471.2 47.98,-1471.2 47.98,-1291.2 368.02,-1291.2 368.02,-1471.2"/>
<text xml:space="preserve" text-anchor="start" x="134.63" y="-1393.2" font-family="Arial" font-size="20.00" fill="#eff6ff">Provider Binding</text>
<text xml:space="preserve" text-anchor="start" x="74" y="-1370.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">Deterministic eligibility + priority provider</text>
<text xml:space="preserve" text-anchor="start" x="178.4" y="-1352.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">selection</text>
</g>
<!-- ffmpegbaseline -->
<g id="node8" class="node">
<title>ffmpegbaseline</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="560.02,-1148.4 239.98,-1148.4 239.98,-968.4 560.02,-968.4 560.02,-1148.4"/>
<text xml:space="preserve" text-anchor="start" x="293.29" y="-1061.4" font-family="Arial" font-size="20.00" fill="#eff6ff">FFmpeg/libass Baseline</text>
<text xml:space="preserve" text-anchor="start" x="300.35" y="-1038.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">Production rendering baseline</text>
</g>
<!-- productruntime -->
<g id="node9" class="node">
<title>productruntime</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="750.02,-825.6 429.98,-825.6 429.98,-645.6 750.02,-645.6 750.02,-825.6"/>
<text xml:space="preserve" text-anchor="start" x="515.52" y="-738.6" font-family="Arial" font-size="20.00" fill="#eff6ff">Product Runtime</text>
<text xml:space="preserve" text-anchor="start" x="489.53" y="-715.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Product lifecycle management</text>
</g>
<!-- storageruntime -->
<g id="node10" class="node">
<title>storageruntime</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1035.02,-502.8 714.98,-502.8 714.98,-322.8 1035.02,-322.8 1035.02,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="799.96" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage Runtime</text>
<text xml:space="preserve" text-anchor="start" x="752.43" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Storage/materialization management</text>
</g>
<!-- platformapp -->
<g id="node11" class="node">
<title>platformapp</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1502.02,-2116.8 1181.98,-2116.8 1181.98,-1936.8 1502.02,-1936.8 1502.02,-2116.8"/>
<text xml:space="preserve" text-anchor="start" x="1285.86" y="-2029.8" font-family="Arial" font-size="20.00" fill="#eff6ff">platform&#45;app</text>
<text xml:space="preserve" text-anchor="start" x="1265.7" y="-2006.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Spring Boot entry point</text>
</g>
<!-- storage -->
<g id="node12" class="node">
<title>storage</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1035.02,-180 714.98,-180 714.98,0 1035.02,0 1035.02,-180"/>
<text xml:space="preserve" text-anchor="start" x="839.98" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage</text>
<text xml:space="preserve" text-anchor="start" x="761.62" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Object storage / shared filesystem</text>
</g>
<!-- timelineedit&#45;&gt;captiontemplate -->
<g id="edge3" class="edge">
<title>timelineedit&#45;&gt;captiontemplate</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M232,-2259.67C232,-2218.47 232,-2169.36 232,-2126.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="234.63,-2127.16 232,-2119.66 229.38,-2127.16 234.63,-2127.16"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="232,-2176.8 232,-2199.6 376.54,-2199.6 376.54,-2176.8 232,-2176.8"/>
<text xml:space="preserve" text-anchor="start" x="235" y="-2182.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">generates typed intent</text>
</g>
<!-- faketestlayer&#45;&gt;previewrenderjobservice -->
<g id="edge4" class="edge">
<title>faketestlayer&#45;&gt;previewrenderjobservice</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M897.19,-2259.79C851.65,-2140.14 770.85,-1927.88 723.49,-1803.48"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="726.05,-1802.84 720.93,-1796.76 721.15,-1804.71 726.05,-1802.84"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="840.82,-2015.4 840.82,-2038.2 876.39,-2038.2 876.39,-2015.4 840.82,-2015.4"/>
<text xml:space="preserve" text-anchor="start" x="843.82" y="-2021.2" font-family="Arial" font-size="14.00" fill="#c9c9c9">tests</text>
</g>
<!-- faketestlayer&#45;&gt;previewartifactqueryservice -->
<g id="edge5" class="edge">
<title>faketestlayer&#45;&gt;previewartifactqueryservice</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M990.8,-2259.77C1026.91,-2197.58 1066,-2111.04 1066,-2027.8 1066,-2027.8 1066,-2027.8 1066,-1380.2 1066,-1301.52 1033.15,-1218.84 1001.26,-1157.13"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1003.78,-1156.28 997.97,-1150.86 999.13,-1158.72 1003.78,-1156.28"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1066,-1692.6 1066,-1715.4 1101.57,-1715.4 1101.57,-1692.6 1066,-1692.6"/>
<text xml:space="preserve" text-anchor="start" x="1069" y="-1698.4" font-family="Arial" font-size="14.00" fill="#c9c9c9">tests</text>
</g>
<!-- captiontemplate&#45;&gt;assstylemapper -->
<g id="edge6" class="edge">
<title>captiontemplate&#45;&gt;assstylemapper</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M232,-1936.87C232,-1895.67 232,-1846.56 232,-1804.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="234.63,-1804.36 232,-1796.86 229.38,-1804.36 234.63,-1804.36"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="232,-1854 232,-1876.8 394.4,-1876.8 394.4,-1854 232,-1854"/>
<text xml:space="preserve" text-anchor="start" x="235" y="-1859.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">maps to ASS parameters</text>
</g>
<!-- previewrenderjobservice&#45;&gt;providerbinding -->
<g id="edge7" class="edge">
<title>previewrenderjobservice&#45;&gt;providerbinding</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M553.49,-1614.07C489.89,-1571.39 413.66,-1520.22 349,-1476.83"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="350.56,-1474.72 342.87,-1472.72 347.64,-1479.08 350.56,-1474.72"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="461.33,-1531.2 461.33,-1554 552.93,-1554 552.93,-1531.2 461.33,-1531.2"/>
<text xml:space="preserve" text-anchor="start" x="464.33" y="-1537" font-family="Arial" font-size="14.00" fill="#c9c9c9">compiles plan</text>
</g>
<!-- previewrenderjobservice&#45;&gt;ffmpegbaseline -->
<g id="edge8" class="edge">
<title>previewrenderjobservice&#45;&gt;ffmpegbaseline</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M652.1,-1614.3C613.22,-1514.42 546.28,-1347.86 480,-1208.4 472.08,-1191.73 463.22,-1174.26 454.37,-1157.4"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="456.7,-1156.18 450.87,-1150.77 452.05,-1158.63 456.7,-1156.18"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="593.77,-1369.8 593.77,-1392.6 707.94,-1392.6 707.94,-1369.8 593.77,-1369.8"/>
<text xml:space="preserve" text-anchor="start" x="596.77" y="-1375.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">executes preview</text>
</g>
<!-- previewrenderjobservice&#45;&gt;productruntime -->
<g id="edge9" class="edge">
<title>previewrenderjobservice&#45;&gt;productruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M709.29,-1614.25C719.38,-1571.37 730.01,-1518.96 735,-1471.2 759.07,-1240.93 679.47,-975.51 628.79,-835.3"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="631.27,-834.42 626.23,-828.27 626.33,-836.21 631.27,-834.42"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="728.55,-1208.4 728.55,-1231.2 831.05,-1231.2 831.05,-1208.4 728.55,-1208.4"/>
<text xml:space="preserve" text-anchor="start" x="731.55" y="-1214.2" font-family="Arial" font-size="14.00" fill="#c9c9c9">creates product</text>
</g>
<!-- previewartifactqueryservice&#45;&gt;productruntime -->
<g id="edge10" class="edge">
<title>previewartifactqueryservice&#45;&gt;productruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M844.42,-968.47C797.97,-926.13 742.36,-875.46 695,-832.29"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="697.02,-830.58 689.71,-827.47 693.48,-834.46 697.02,-830.58"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="776.55,-885.6 776.55,-908.4 879.06,-908.4 879.06,-885.6 776.55,-885.6"/>
<text xml:space="preserve" text-anchor="start" x="779.55" y="-891.4" font-family="Arial" font-size="14.00" fill="#c9c9c9">queries product</text>
</g>
<!-- previewartifactqueryservice&#45;&gt;storageruntime -->
<g id="edge11" class="edge">
<title>previewartifactqueryservice&#45;&gt;storageruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M932.75,-968.59C920.32,-849.18 898.29,-637.53 885.33,-513.03"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="887.96,-512.93 884.57,-505.75 882.74,-513.48 887.96,-512.93"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="917.34,-724.2 917.34,-747 1019.84,-747 1019.84,-724.2 917.34,-724.2"/>
<text xml:space="preserve" text-anchor="start" x="920.34" y="-730" font-family="Arial" font-size="14.00" fill="#c9c9c9">queries storage</text>
</g>
<!-- assstylemapper&#45;&gt;providerbinding -->
<g id="edge12" class="edge">
<title>assstylemapper&#45;&gt;providerbinding</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M219.53,-1614.11C217.15,-1594.35 214.92,-1573.49 213.4,-1554 211.57,-1530.55 210.36,-1505.25 209.55,-1481.46"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="212.18,-1481.45 209.32,-1474.04 206.93,-1481.62 212.18,-1481.45"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="213.4,-1531.2 213.4,-1554 326,-1554 326,-1531.2 213.4,-1531.2"/>
<text xml:space="preserve" text-anchor="start" x="216.4" y="-1537" font-family="Arial" font-size="14.00" fill="#c9c9c9">resolves provider</text>
</g>
<!-- providerbinding&#45;&gt;ffmpegbaseline -->
<g id="edge13" class="edge">
<title>providerbinding&#45;&gt;ffmpegbaseline</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M261.23,-1291.27C286.15,-1249.63 315.9,-1199.92 341.45,-1157.23"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="343.68,-1158.61 345.28,-1150.83 339.18,-1155.91 343.68,-1158.61"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="309.76,-1208.4 309.76,-1231.2 452.67,-1231.2 452.67,-1208.4 309.76,-1208.4"/>
<text xml:space="preserve" text-anchor="start" x="312.76" y="-1214.2" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes PRODUCTION</text>
</g>
<!-- ffmpegbaseline&#45;&gt;productruntime -->
<g id="edge14" class="edge">
<title>ffmpegbaseline&#45;&gt;productruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M452.67,-968.47C477.33,-926.83 506.77,-877.12 532.06,-834.43"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="534.28,-835.82 535.85,-828.03 529.77,-833.15 534.28,-835.82"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="500.7,-885.6 500.7,-908.4 607.1,-908.4 607.1,-885.6 500.7,-885.6"/>
<text xml:space="preserve" text-anchor="start" x="503.7" y="-891.4" font-family="Arial" font-size="14.00" fill="#c9c9c9">produces output</text>
</g>
<!-- productruntime&#45;&gt;storageruntime -->
<g id="edge15" class="edge">
<title>productruntime&#45;&gt;storageruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M669.01,-645.67C706.38,-603.6 751.08,-553.28 789.28,-510.29"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="791.05,-512.24 794.07,-504.89 787.13,-508.76 791.05,-512.24"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="741.05,-562.8 741.05,-585.6 858.32,-585.6 858.32,-562.8 741.05,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="744.05" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">manages lifecycle</text>
</g>
<!-- storageruntime&#45;&gt;storage -->
<g id="edge16" class="edge">
<title>storageruntime&#45;&gt;storage</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M875,-322.87C875,-281.67 875,-232.56 875,-190.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="877.63,-190.36 875,-182.86 872.38,-190.36 877.63,-190.36"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="875,-240 875,-262.8 982.14,-262.8 982.14,-240 875,-240"/>
<text xml:space="preserve" text-anchor="start" x="878" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">persists artifacts</text>
</g>
<!-- platformapp&#45;&gt;previewrenderjobservice -->
<g id="edge1" class="edge">
<title>platformapp&#45;&gt;previewrenderjobservice</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1182.31,-1943.44C1177.83,-1941.2 1173.39,-1938.98 1169,-1936.8 1065.33,-1885.27 948.28,-1829.05 855.38,-1784.88"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="856.53,-1782.52 848.63,-1781.67 854.27,-1787.26 856.53,-1782.52"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1036.89,-1854 1036.89,-1876.8 1121.49,-1876.8 1121.49,-1854 1036.89,-1854"/>
<text xml:space="preserve" text-anchor="start" x="1039.89" y="-1859.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">exposes API</text>
</g>
<!-- platformapp&#45;&gt;previewartifactqueryservice -->
<g id="edge2" class="edge">
<title>platformapp&#45;&gt;previewartifactqueryservice</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1329.88,-1937.01C1305.4,-1780.27 1239.49,-1448.4 1094,-1208.4 1082.79,-1189.9 1068.75,-1172.07 1053.78,-1155.58"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1055.92,-1154.03 1048.9,-1150.31 1052.07,-1157.6 1055.92,-1154.03"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1242.34,-1531.2 1242.34,-1554 1326.94,-1554 1326.94,-1531.2 1242.34,-1531.2"/>
<text xml:space="preserve" text-anchor="start" x="1245.34" y="-1537" font-family="Arial" font-size="14.00" fill="#c9c9c9">exposes API</text>
</g>
</g>
</svg>
`;case`captionTemplateBoundary`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="398pt" height="1501pt"
 viewBox="0.00 0.00 398.00 1501.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 1486.25)">
<!-- timelineedit -->
<g id="node1" class="node">
<title>timelineedit</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="358.6,-1471.2 9.15,-1471.2 9.15,-1291.2 358.6,-1291.2 358.6,-1471.2"/>
<text xml:space="preserve" text-anchor="start" x="77.17" y="-1393.2" font-family="Arial" font-size="20.00" fill="#eff6ff">Timeline Edit Command</text>
<text xml:space="preserve" text-anchor="start" x="29.21" y="-1370.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">TL.0: Sealed interface with 12 typed command</text>
<text xml:space="preserve" text-anchor="start" x="158.87" y="-1352.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">records</text>
</g>
<!-- captiontemplate -->
<g id="node2" class="node">
<title>captiontemplate</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="351.09,-1148.4 16.66,-1148.4 16.66,-968.4 351.09,-968.4 351.09,-1148.4"/>
<text xml:space="preserve" text-anchor="start" x="104.95" y="-1070.4" font-family="Arial" font-size="20.00" fill="#eff6ff">Caption Template</text>
<text xml:space="preserve" text-anchor="start" x="36.72" y="-1047.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">CT.0: Typed intent model for caption/subtitle</text>
<text xml:space="preserve" text-anchor="start" x="152.19" y="-1029.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">rendering</text>
</g>
<!-- assstylemapper -->
<g id="node3" class="node">
<title>assstylemapper</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="367.75,-825.6 0,-825.6 0,-645.6 367.75,-645.6 367.75,-825.6"/>
<text xml:space="preserve" text-anchor="start" x="111.07" y="-747.6" font-family="Arial" font-size="20.00" fill="#eff6ff">AssStyleMapper</text>
<text xml:space="preserve" text-anchor="start" x="20.06" y="-724.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">CT.0: Maps CaptionTemplateSpec to ASS format</text>
<text xml:space="preserve" text-anchor="start" x="145.94" y="-706.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">parameters</text>
</g>
<!-- providerbinding -->
<g id="node4" class="node">
<title>providerbinding</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="343.9,-502.8 23.86,-502.8 23.86,-322.8 343.9,-322.8 343.9,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="110.5" y="-424.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Provider Binding</text>
<text xml:space="preserve" text-anchor="start" x="49.88" y="-401.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Deterministic eligibility + priority provider</text>
<text xml:space="preserve" text-anchor="start" x="154.28" y="-383.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">selection</text>
</g>
<!-- ffmpegbaseline -->
<g id="node5" class="node">
<title>ffmpegbaseline</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="343.9,-180 23.86,-180 23.86,0 343.9,0 343.9,-180"/>
<text xml:space="preserve" text-anchor="start" x="77.16" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">FFmpeg/libass Baseline</text>
<text xml:space="preserve" text-anchor="start" x="84.23" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Production rendering baseline</text>
</g>
<!-- timelineedit&#45;&gt;captiontemplate -->
<g id="edge1" class="edge">
<title>timelineedit&#45;&gt;captiontemplate</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M183.88,-1291.27C183.88,-1250.07 183.88,-1200.96 183.88,-1158.57"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="186.5,-1158.76 183.88,-1151.26 181.25,-1158.76 186.5,-1158.76"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="183.88,-1208.4 183.88,-1231.2 328.42,-1231.2 328.42,-1208.4 183.88,-1208.4"/>
<text xml:space="preserve" text-anchor="start" x="186.88" y="-1214.2" font-family="Arial" font-size="14.00" fill="#c9c9c9">generates typed intent</text>
</g>
<!-- captiontemplate&#45;&gt;assstylemapper -->
<g id="edge2" class="edge">
<title>captiontemplate&#45;&gt;assstylemapper</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M183.88,-968.47C183.88,-927.27 183.88,-878.16 183.88,-835.77"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="186.5,-835.96 183.88,-828.46 181.25,-835.96 186.5,-835.96"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="183.88,-885.6 183.88,-908.4 346.28,-908.4 346.28,-885.6 183.88,-885.6"/>
<text xml:space="preserve" text-anchor="start" x="186.88" y="-891.4" font-family="Arial" font-size="14.00" fill="#c9c9c9">maps to ASS parameters</text>
</g>
<!-- assstylemapper&#45;&gt;providerbinding -->
<g id="edge3" class="edge">
<title>assstylemapper&#45;&gt;providerbinding</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M183.88,-645.67C183.88,-604.47 183.88,-555.36 183.88,-512.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="186.5,-513.16 183.88,-505.66 181.25,-513.16 186.5,-513.16"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="183.88,-562.8 183.88,-585.6 296.48,-585.6 296.48,-562.8 183.88,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="186.88" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">resolves provider</text>
</g>
<!-- providerbinding&#45;&gt;ffmpegbaseline -->
<g id="edge4" class="edge">
<title>providerbinding&#45;&gt;ffmpegbaseline</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M183.88,-322.87C183.88,-281.67 183.88,-232.56 183.88,-190.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="186.5,-190.36 183.88,-182.86 181.25,-190.36 186.5,-190.36"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="183.88,-240 183.88,-262.8 326.79,-262.8 326.79,-240 183.88,-240"/>
<text xml:space="preserve" text-anchor="start" x="186.88" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes PRODUCTION</text>
</g>
</g>
</svg>
`;case`productStorageBoundary`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="350pt" height="1178pt"
 viewBox="0.00 0.00 350.00 1178.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 1163.45)">
<!-- ffmpegbaseline -->
<g id="node1" class="node">
<title>ffmpegbaseline</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-1148.4 0,-1148.4 0,-968.4 320.04,-968.4 320.04,-1148.4"/>
<text xml:space="preserve" text-anchor="start" x="53.31" y="-1061.4" font-family="Arial" font-size="20.00" fill="#eff6ff">FFmpeg/libass Baseline</text>
<text xml:space="preserve" text-anchor="start" x="60.37" y="-1038.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">Production rendering baseline</text>
</g>
<!-- productruntime -->
<g id="node2" class="node">
<title>productruntime</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-825.6 0,-825.6 0,-645.6 320.04,-645.6 320.04,-825.6"/>
<text xml:space="preserve" text-anchor="start" x="85.54" y="-738.6" font-family="Arial" font-size="20.00" fill="#eff6ff">Product Runtime</text>
<text xml:space="preserve" text-anchor="start" x="59.55" y="-715.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Product lifecycle management</text>
</g>
<!-- storageruntime -->
<g id="node3" class="node">
<title>storageruntime</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-502.8 0,-502.8 0,-322.8 320.04,-322.8 320.04,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="84.98" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage Runtime</text>
<text xml:space="preserve" text-anchor="start" x="37.45" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Storage/materialization management</text>
</g>
<!-- storage -->
<g id="node4" class="node">
<title>storage</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-180 0,-180 0,0 320.04,0 320.04,-180"/>
<text xml:space="preserve" text-anchor="start" x="125" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage</text>
<text xml:space="preserve" text-anchor="start" x="46.64" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Object storage / shared filesystem</text>
</g>
<!-- ffmpegbaseline&#45;&gt;productruntime -->
<g id="edge1" class="edge">
<title>ffmpegbaseline&#45;&gt;productruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M160.02,-968.47C160.02,-927.27 160.02,-878.16 160.02,-835.77"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="162.65,-835.96 160.02,-828.46 157.4,-835.96 162.65,-835.96"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="160.02,-885.6 160.02,-908.4 266.43,-908.4 266.43,-885.6 160.02,-885.6"/>
<text xml:space="preserve" text-anchor="start" x="163.02" y="-891.4" font-family="Arial" font-size="14.00" fill="#c9c9c9">produces output</text>
</g>
<!-- productruntime&#45;&gt;storageruntime -->
<g id="edge2" class="edge">
<title>productruntime&#45;&gt;storageruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M160.02,-645.67C160.02,-604.47 160.02,-555.36 160.02,-512.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="162.65,-513.16 160.02,-505.66 157.4,-513.16 162.65,-513.16"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="160.02,-562.8 160.02,-585.6 277.3,-585.6 277.3,-562.8 160.02,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="163.02" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">manages lifecycle</text>
</g>
<!-- storageruntime&#45;&gt;storage -->
<g id="edge3" class="edge">
<title>storageruntime&#45;&gt;storage</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M160.02,-322.87C160.02,-281.67 160.02,-232.56 160.02,-190.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="162.65,-190.36 160.02,-182.86 157.4,-190.36 162.65,-190.36"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="160.02,-240 160.02,-262.8 267.16,-262.8 267.16,-240 160.02,-240"/>
<text xml:space="preserve" text-anchor="start" x="163.02" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">persists artifacts</text>
</g>
</g>
</svg>
`;case`providerExecutionBoundary`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="1268pt" height="533pt"
 viewBox="0.00 0.00 1268.00 533.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 517.85)">
<!-- providerbinding -->
<g id="node1" class="node">
<title>providerbinding</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="541.04,-502.8 221,-502.8 221,-322.8 541.04,-322.8 541.04,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="307.65" y="-424.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Provider Binding</text>
<text xml:space="preserve" text-anchor="start" x="247.02" y="-401.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Deterministic eligibility + priority provider</text>
<text xml:space="preserve" text-anchor="start" x="351.42" y="-383.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">selection</text>
</g>
<!-- ffmpegbaseline -->
<g id="node2" class="node">
<title>ffmpegbaseline</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-180 0,-180 0,0 320.04,0 320.04,-180"/>
<text xml:space="preserve" text-anchor="start" x="53.31" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">FFmpeg/libass Baseline</text>
<text xml:space="preserve" text-anchor="start" x="60.37" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Production rendering baseline</text>
</g>
<!-- opencue -->
<g id="node3" class="node">
<title>opencue</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="776.47,-180 429.57,-180 429.57,0 776.47,0 776.47,-180"/>
<text xml:space="preserve" text-anchor="start" x="560.21" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">OpenCue</text>
<text xml:space="preserve" text-anchor="start" x="449.62" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">ExecutionEnvironment only — NOT a Provider</text>
</g>
<!-- remotion -->
<g id="node4" class="node">
<title>remotion</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1237.57,-180 886.47,-180 886.47,0 1237.57,0 1237.57,-180"/>
<text xml:space="preserve" text-anchor="start" x="1019.22" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Remotion</text>
<text xml:space="preserve" text-anchor="start" x="906.53" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Non&#45;production/POC subtitle template provider</text>
</g>
<!-- providerbinding&#45;&gt;ffmpegbaseline -->
<g id="edge1" class="edge">
<title>providerbinding&#45;&gt;ffmpegbaseline</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M319.75,-322.87C290.95,-281.06 256.54,-231.11 227.05,-188.29"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="229.35,-187.01 222.93,-182.32 225.03,-189.99 229.35,-187.01"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="277.15,-240 277.15,-262.8 420.06,-262.8 420.06,-240 277.15,-240"/>
<text xml:space="preserve" text-anchor="start" x="280.15" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes PRODUCTION</text>
</g>
<!-- providerbinding&#45;&gt;opencue -->
<g id="edge2" class="edge">
<title>providerbinding&#45;&gt;opencue</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M442.56,-322.87C471.5,-281.06 506.06,-231.11 535.69,-188.29"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="537.72,-189.98 539.83,-182.32 533.4,-186.99 537.72,-189.98"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="498.68,-240 498.68,-262.8 662.65,-262.8 662.65,-240 498.68,-240"/>
<text xml:space="preserve" text-anchor="start" x="501.68" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes ExecutionEnv only</text>
</g>
<!-- providerbinding&#45;&gt;remotion -->
<g id="edge3" class="edge">
<title>providerbinding&#45;&gt;remotion</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M540.68,-336.59C641.43,-289.13 771.93,-227.65 877.16,-178.08"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="878.21,-180.49 883.88,-174.92 875.98,-175.74 878.21,-180.49"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="741.94,-240 741.94,-262.8 850.65,-262.8 850.65,-240 741.94,-240"/>
<text xml:space="preserve" text-anchor="start" x="744.94" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes POC only</text>
</g>
</g>
</svg>
`;case`previewRenderJobApiFlow`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="849pt" height="1824pt"
 viewBox="0.00 0.00 849.00 1824.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 1809.05)">
<!-- previewrenderjobservice -->
<g id="node1" class="node">
<title>previewrenderjobservice</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="700.04,-1794 380,-1794 380,-1614 700.04,-1614 700.04,-1794"/>
<text xml:space="preserve" text-anchor="start" x="413.85" y="-1707" font-family="Arial" font-size="20.00" fill="#eff6ff">Preview Render Job Service</text>
<text xml:space="preserve" text-anchor="start" x="428.3" y="-1684" font-family="Arial" font-size="15.00" fill="#bfdbfe">VS.1: Preview render job lifecycle</text>
</g>
<!-- providerbinding -->
<g id="node2" class="node">
<title>providerbinding</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-1471.2 0,-1471.2 0,-1291.2 320.04,-1291.2 320.04,-1471.2"/>
<text xml:space="preserve" text-anchor="start" x="86.65" y="-1393.2" font-family="Arial" font-size="20.00" fill="#eff6ff">Provider Binding</text>
<text xml:space="preserve" text-anchor="start" x="26.02" y="-1370.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">Deterministic eligibility + priority provider</text>
<text xml:space="preserve" text-anchor="start" x="130.42" y="-1352.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">selection</text>
</g>
<!-- ffmpegbaseline -->
<g id="node3" class="node">
<title>ffmpegbaseline</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="654.04,-1148.4 334,-1148.4 334,-968.4 654.04,-968.4 654.04,-1148.4"/>
<text xml:space="preserve" text-anchor="start" x="387.31" y="-1061.4" font-family="Arial" font-size="20.00" fill="#eff6ff">FFmpeg/libass Baseline</text>
<text xml:space="preserve" text-anchor="start" x="394.37" y="-1038.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">Production rendering baseline</text>
</g>
<!-- productruntime -->
<g id="node4" class="node">
<title>productruntime</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="817.04,-825.6 497,-825.6 497,-645.6 817.04,-645.6 817.04,-825.6"/>
<text xml:space="preserve" text-anchor="start" x="582.54" y="-738.6" font-family="Arial" font-size="20.00" fill="#eff6ff">Product Runtime</text>
<text xml:space="preserve" text-anchor="start" x="556.55" y="-715.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Product lifecycle management</text>
</g>
<!-- storageruntime -->
<g id="node5" class="node">
<title>storageruntime</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="817.04,-502.8 497,-502.8 497,-322.8 817.04,-322.8 817.04,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="581.98" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage Runtime</text>
<text xml:space="preserve" text-anchor="start" x="534.45" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Storage/materialization management</text>
</g>
<!-- storage -->
<g id="node6" class="node">
<title>storage</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="817.04,-180 497,-180 497,0 817.04,0 817.04,-180"/>
<text xml:space="preserve" text-anchor="start" x="622" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage</text>
<text xml:space="preserve" text-anchor="start" x="543.64" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Object storage / shared filesystem</text>
</g>
<!-- previewrenderjobservice&#45;&gt;providerbinding -->
<g id="edge1" class="edge">
<title>previewrenderjobservice&#45;&gt;providerbinding</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M434.67,-1614.07C384.43,-1571.65 324.25,-1520.85 273.05,-1477.62"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="274.98,-1475.81 267.56,-1472.98 271.59,-1479.83 274.98,-1475.81"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="361.41,-1531.2 361.41,-1554 453.01,-1554 453.01,-1531.2 361.41,-1531.2"/>
<text xml:space="preserve" text-anchor="start" x="364.41" y="-1537" font-family="Arial" font-size="14.00" fill="#c9c9c9">compiles plan</text>
</g>
<!-- previewrenderjobservice&#45;&gt;ffmpegbaseline -->
<g id="edge2" class="edge">
<title>previewrenderjobservice&#45;&gt;ffmpegbaseline</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M533.67,-1614.19C525.14,-1494.78 510.01,-1283.13 501.11,-1158.63"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="503.75,-1158.65 500.59,-1151.35 498.51,-1159.02 503.75,-1158.65"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="523.09,-1369.8 523.09,-1392.6 637.25,-1392.6 637.25,-1369.8 523.09,-1369.8"/>
<text xml:space="preserve" text-anchor="start" x="526.09" y="-1375.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">executes preview</text>
</g>
<!-- previewrenderjobservice&#45;&gt;productruntime -->
<g id="edge3" class="edge">
<title>previewrenderjobservice&#45;&gt;productruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M597.12,-1614.1C621.44,-1572.27 647.87,-1520.78 664.02,-1471.2 741.79,-1232.43 711.4,-1159.5 714.02,-908.4 714.13,-898.27 715.86,-895.56 714.02,-885.6 710.93,-868.87 706.04,-851.58 700.39,-834.98"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="702.95,-834.35 697.99,-828.14 698,-836.09 702.95,-834.35"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="716.64,-1208.4 716.64,-1231.2 819.13,-1231.2 819.13,-1208.4 716.64,-1208.4"/>
<text xml:space="preserve" text-anchor="start" x="719.64" y="-1214.2" font-family="Arial" font-size="14.00" fill="#c9c9c9">creates product</text>
</g>
<!-- providerbinding&#45;&gt;ffmpegbaseline -->
<g id="edge4" class="edge">
<title>providerbinding&#45;&gt;ffmpegbaseline</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M235.21,-1291.35C259.57,-1263.96 287.25,-1234.22 314.11,-1208.4 332.78,-1190.46 353.32,-1172.19 373.65,-1154.89"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="375.08,-1157.11 379.11,-1150.26 371.69,-1153.11 375.08,-1157.11"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="314.11,-1208.4 314.11,-1231.2 457.02,-1231.2 457.02,-1208.4 314.11,-1208.4"/>
<text xml:space="preserve" text-anchor="start" x="317.11" y="-1214.2" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes PRODUCTION</text>
</g>
<!-- ffmpegbaseline&#45;&gt;productruntime -->
<g id="edge5" class="edge">
<title>ffmpegbaseline&#45;&gt;productruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M539.21,-968.47C560.32,-926.92 585.51,-877.33 607.18,-834.7"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="609.46,-836 610.51,-828.13 604.78,-833.63 609.46,-836"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="580.41,-885.6 580.41,-908.4 686.81,-908.4 686.81,-885.6 580.41,-885.6"/>
<text xml:space="preserve" text-anchor="start" x="583.41" y="-891.4" font-family="Arial" font-size="14.00" fill="#c9c9c9">produces output</text>
</g>
<!-- productruntime&#45;&gt;storageruntime -->
<g id="edge6" class="edge">
<title>productruntime&#45;&gt;storageruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M657.02,-645.67C657.02,-604.47 657.02,-555.36 657.02,-512.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="659.65,-513.16 657.02,-505.66 654.4,-513.16 659.65,-513.16"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="657.02,-562.8 657.02,-585.6 774.3,-585.6 774.3,-562.8 657.02,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="660.02" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">manages lifecycle</text>
</g>
<!-- storageruntime&#45;&gt;storage -->
<g id="edge7" class="edge">
<title>storageruntime&#45;&gt;storage</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M657.02,-322.87C657.02,-281.67 657.02,-232.56 657.02,-190.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="659.65,-190.36 657.02,-182.86 654.4,-190.36 659.65,-190.36"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="657.02,-240 657.02,-262.8 764.16,-262.8 764.16,-240 657.02,-240"/>
<text xml:space="preserve" text-anchor="start" x="660.02" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">persists artifacts</text>
</g>
</g>
</svg>
`;case`productArtifactResponseFlow`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="730pt" height="1178pt"
 viewBox="0.00 0.00 730.00 1178.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 1163.45)">
<!-- previewartifactqueryservice -->
<g id="node1" class="node">
<title>previewartifactqueryservice</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="700.04,-1148.4 380,-1148.4 380,-968.4 700.04,-968.4 700.04,-1148.4"/>
<text xml:space="preserve" text-anchor="start" x="404.42" y="-1061.4" font-family="Arial" font-size="20.00" fill="#eff6ff">Preview Artifact Query Service</text>
<text xml:space="preserve" text-anchor="start" x="414.96" y="-1038.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">VS.1: Product/artifact metadata query</text>
</g>
<!-- productruntime -->
<g id="node2" class="node">
<title>productruntime</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-825.6 0,-825.6 0,-645.6 320.04,-645.6 320.04,-825.6"/>
<text xml:space="preserve" text-anchor="start" x="85.54" y="-738.6" font-family="Arial" font-size="20.00" fill="#eff6ff">Product Runtime</text>
<text xml:space="preserve" text-anchor="start" x="59.55" y="-715.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Product lifecycle management</text>
</g>
<!-- storageruntime -->
<g id="node3" class="node">
<title>storageruntime</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="510.04,-502.8 190,-502.8 190,-322.8 510.04,-322.8 510.04,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="274.98" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage Runtime</text>
<text xml:space="preserve" text-anchor="start" x="227.45" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Storage/materialization management</text>
</g>
<!-- storage -->
<g id="node4" class="node">
<title>storage</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="510.04,-180 190,-180 190,0 510.04,0 510.04,-180"/>
<text xml:space="preserve" text-anchor="start" x="315" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage</text>
<text xml:space="preserve" text-anchor="start" x="236.64" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Object storage / shared filesystem</text>
</g>
<!-- previewartifactqueryservice&#45;&gt;productruntime -->
<g id="edge1" class="edge">
<title>previewartifactqueryservice&#45;&gt;productruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M427.5,-968.42C403.26,-948.85 377.89,-928.09 354.52,-908.4 325.58,-884.02 294.69,-857.14 266.25,-832.03"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="268.31,-830.35 260.95,-827.35 264.83,-834.28 268.31,-830.35"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="354.52,-885.6 354.52,-908.4 457.02,-908.4 457.02,-885.6 354.52,-885.6"/>
<text xml:space="preserve" text-anchor="start" x="357.52" y="-891.4" font-family="Arial" font-size="14.00" fill="#c9c9c9">queries product</text>
</g>
<!-- previewartifactqueryservice&#45;&gt;storageruntime -->
<g id="edge2" class="edge">
<title>previewartifactqueryservice&#45;&gt;storageruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M518.3,-968.71C493.35,-869.2 450.03,-703.3 405.02,-562.8 399.78,-546.45 393.84,-529.23 387.84,-512.54"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="390.36,-511.79 385.34,-505.63 385.42,-513.58 390.36,-511.79"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="480.68,-724.2 480.68,-747 583.18,-747 583.18,-724.2 480.68,-724.2"/>
<text xml:space="preserve" text-anchor="start" x="483.68" y="-730" font-family="Arial" font-size="14.00" fill="#c9c9c9">queries storage</text>
</g>
<!-- productruntime&#45;&gt;storageruntime -->
<g id="edge3" class="edge">
<title>productruntime&#45;&gt;storageruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M212.69,-645.67C237.35,-604.03 266.79,-554.32 292.08,-511.63"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="294.3,-513.02 295.87,-505.23 289.79,-510.35 294.3,-513.02"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="260.72,-562.8 260.72,-585.6 377.99,-585.6 377.99,-562.8 260.72,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="263.72" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">manages lifecycle</text>
</g>
<!-- storageruntime&#45;&gt;storage -->
<g id="edge4" class="edge">
<title>storageruntime&#45;&gt;storage</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M350.02,-322.87C350.02,-281.67 350.02,-232.56 350.02,-190.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="352.65,-190.36 350.02,-182.86 347.4,-190.36 352.65,-190.36"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="350.02,-240 350.02,-262.8 457.16,-262.8 457.16,-240 350.02,-240"/>
<text xml:space="preserve" text-anchor="start" x="353.02" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">persists artifacts</text>
</g>
</g>
</svg>
`;case`headlessApiValidationFlow`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="780pt" height="533pt"
 viewBox="0.00 0.00 780.00 533.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 517.85)">
<!-- faketestlayer -->
<g id="node1" class="node">
<title>faketestlayer</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="535.04,-502.8 215,-502.8 215,-322.8 535.04,-322.8 535.04,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="302.77" y="-424.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Fake Test Layer</text>
<text xml:space="preserve" text-anchor="start" x="242.47" y="-401.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">VS.1&#45;TEST.1: Fake implementations for</text>
<text xml:space="preserve" text-anchor="start" x="315.81" y="-383.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">integration testing</text>
</g>
<!-- previewrenderjobservice -->
<g id="node2" class="node">
<title>previewrenderjobservice</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-180 0,-180 0,0 320.04,0 320.04,-180"/>
<text xml:space="preserve" text-anchor="start" x="33.85" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Preview Render Job Service</text>
<text xml:space="preserve" text-anchor="start" x="48.3" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">VS.1: Preview render job lifecycle</text>
</g>
<!-- previewartifactqueryservice -->
<g id="node3" class="node">
<title>previewartifactqueryservice</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="750.04,-180 430,-180 430,0 750.04,0 750.04,-180"/>
<text xml:space="preserve" text-anchor="start" x="454.42" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Preview Artifact Query Service</text>
<text xml:space="preserve" text-anchor="start" x="464.96" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">VS.1: Product/artifact metadata query</text>
</g>
<!-- faketestlayer&#45;&gt;previewrenderjobservice -->
<g id="edge1" class="edge">
<title>faketestlayer&#45;&gt;previewrenderjobservice</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M315.42,-322.87C287.45,-281.14 254.06,-231.31 225.4,-188.56"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="227.59,-187.11 221.24,-182.34 223.23,-190.04 227.59,-187.11"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="273.97,-240 273.97,-262.8 309.53,-262.8 309.53,-240 273.97,-240"/>
<text xml:space="preserve" text-anchor="start" x="276.97" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">tests</text>
</g>
<!-- faketestlayer&#45;&gt;previewartifactqueryservice -->
<g id="edge2" class="edge">
<title>faketestlayer&#45;&gt;previewartifactqueryservice</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M434.62,-322.87C462.59,-281.14 495.98,-231.31 524.64,-188.56"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="526.81,-190.04 528.8,-182.34 522.45,-187.11 526.81,-190.04"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="488.97,-240 488.97,-262.8 524.53,-262.8 524.53,-240 488.97,-240"/>
<text xml:space="preserve" text-anchor="start" x="491.97" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">tests</text>
</g>
</g>
</svg>
`;case`storageDeliveryProfileArchitecture`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="1314pt" height="973pt"
 viewBox="0.00 0.00 1314.00 973.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 957.85)">
<g id="clust1" class="cluster">
<title>cluster_storagemodule</title>
<polygon fill="#194b9e" stroke="#1b3d88" points="8,-8 8,-934.8 1276,-934.8 1276,-8 8,-8"/>
<text xml:space="preserve" text-anchor="start" x="16" y="-921.9" font-family="Arial" font-weight="bold" font-size="11.00" fill="#bfdbfe" fill-opacity="0.701961">STORAGE&#45;MODULE</text>
</g>
<!-- storagedeliveryprofilevalidator -->
<g id="node1" class="node">
<title>storagedeliveryprofilevalidator</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-873.6 47.98,-873.6 47.98,-693.6 368.02,-693.6 368.02,-873.6"/>
<text xml:space="preserve" text-anchor="start" x="69.04" y="-795.6" font-family="Arial" font-size="20.00" fill="#eff6ff">StorageDeliveryProfileValidator</text>
<text xml:space="preserve" text-anchor="start" x="70.43" y="-772.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Local validation: access mode, capability,</text>
<text xml:space="preserve" text-anchor="start" x="163.82" y="-754.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">security rules</text>
</g>
<!-- storagedeliveryprofileconfig -->
<g id="node2" class="node">
<title>storagedeliveryprofileconfig</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="798.02,-873.6 477.98,-873.6 477.98,-693.6 798.02,-693.6 798.02,-873.6"/>
<text xml:space="preserve" text-anchor="start" x="509.61" y="-786.6" font-family="Arial" font-size="20.00" fill="#eff6ff">StorageDeliveryProfileConfig</text>
<text xml:space="preserve" text-anchor="start" x="505.84" y="-763.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Spring config binding: storage.delivery.*</text>
</g>
<!-- storagedeliveryprofileregistry -->
<g id="node3" class="node">
<title>storagedeliveryprofileregistry</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1235.88,-873.6 908.12,-873.6 908.12,-693.6 1235.88,-693.6 1235.88,-873.6"/>
<text xml:space="preserve" text-anchor="start" x="935.84" y="-795.6" font-family="Arial" font-size="20.00" fill="#eff6ff">StorageDeliveryProfileRegistry</text>
<text xml:space="preserve" text-anchor="start" x="928.18" y="-772.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Read&#45;only registry: 8 canonical profiles, not</text>
<text xml:space="preserve" text-anchor="start" x="984.04" y="-754.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">used for provider selection</text>
</g>
<!-- storagedeliveryprofile -->
<g id="node4" class="node">
<title>storagedeliveryprofile</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="798.02,-550.8 477.98,-550.8 477.98,-370.8 798.02,-370.8 798.02,-550.8"/>
<text xml:space="preserve" text-anchor="start" x="532.96" y="-472.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage Delivery Profile</text>
<text xml:space="preserve" text-anchor="start" x="517.52" y="-449.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Profile contract: 8 canonical profiles,</text>
<text xml:space="preserve" text-anchor="start" x="548.79" y="-431.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">access modes, capabilities</text>
</g>
<!-- storagedeliveryprofiledto -->
<g id="node5" class="node">
<title>storagedeliveryprofiledto</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="798.02,-228 477.98,-228 477.98,-48 798.02,-48 798.02,-228"/>
<text xml:space="preserve" text-anchor="start" x="514.63" y="-150" font-family="Arial" font-size="20.00" fill="#eff6ff">StorageDeliveryProfile DTO</text>
<text xml:space="preserve" text-anchor="start" x="539.61" y="-127" font-family="Arial" font-size="15.00" fill="#bfdbfe">Internal value objects: Profile,</text>
<text xml:space="preserve" text-anchor="start" x="547.96" y="-109" font-family="Arial" font-size="15.00" fill="#bfdbfe">Capabilities, SecurityPolicy</text>
</g>
<!-- storagedeliveryprofilevalidator&#45;&gt;storagedeliveryprofile -->
<g id="edge1" class="edge">
<title>storagedeliveryprofilevalidator&#45;&gt;storagedeliveryprofile</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M327.21,-693.67C384.3,-651.07 452.72,-600.03 510.81,-556.69"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="512.07,-559.03 516.51,-552.44 508.93,-554.82 512.07,-559.03"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="435.89,-610.8 435.89,-633.6 497.15,-633.6 497.15,-610.8 435.89,-610.8"/>
<text xml:space="preserve" text-anchor="start" x="438.89" y="-616.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">validates</text>
</g>
<!-- storagedeliveryprofileconfig&#45;&gt;storagedeliveryprofile -->
<g id="edge2" class="edge">
<title>storagedeliveryprofileconfig&#45;&gt;storagedeliveryprofile</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M638,-693.67C638,-652.47 638,-603.36 638,-560.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="640.63,-561.16 638,-553.66 635.38,-561.16 640.63,-561.16"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="638,-610.8 638,-633.6 718.72,-633.6 718.72,-610.8 638,-610.8"/>
<text xml:space="preserve" text-anchor="start" x="641" y="-616.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">binds config</text>
</g>
<!-- storagedeliveryprofileregistry&#45;&gt;storagedeliveryprofile -->
<g id="edge3" class="edge">
<title>storagedeliveryprofileregistry&#45;&gt;storagedeliveryprofile</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M951.68,-693.67C894.06,-651.07 825.01,-600.03 766.38,-556.69"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="768.2,-554.78 760.61,-552.43 765.08,-559 768.2,-554.78"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="868.01,-610.8 868.01,-633.6 956.5,-633.6 956.5,-610.8 868.01,-610.8"/>
<text xml:space="preserve" text-anchor="start" x="871.01" y="-616.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">holds profiles</text>
</g>
<!-- storagedeliveryprofile&#45;&gt;storagedeliveryprofiledto -->
<g id="edge4" class="edge">
<title>storagedeliveryprofile&#45;&gt;storagedeliveryprofiledto</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M638,-370.87C638,-329.67 638,-280.56 638,-238.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="640.63,-238.36 638,-230.86 635.38,-238.36 640.63,-238.36"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="638,-288 638,-310.8 734.24,-310.8 734.24,-288 638,-288"/>
<text xml:space="preserve" text-anchor="start" x="641" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">maps to DTOs</text>
</g>
</g>
</svg>
`;case`ingestPreflightPolicyFlow`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="1312pt" height="1618pt"
 viewBox="0.00 0.00 1312.00 1618.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 1603.45)">
<g id="clust1" class="cluster">
<title>cluster_ingestmodule</title>
<polygon fill="#194b9e" stroke="#1b3d88" points="8,-8 8,-1580.4 1274,-1580.4 1274,-8 8,-8"/>
<text xml:space="preserve" text-anchor="start" x="16" y="-1567.5" font-family="Arial" font-weight="bold" font-size="11.00" fill="#bfdbfe" fill-opacity="0.701961">INGEST&#45;MODULE</text>
</g>
<!-- uploadhook -->
<g id="node1" class="node">
<title>uploadhook</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="803.35,-1519.2 478.65,-1519.2 478.65,-1339.2 803.35,-1339.2 803.35,-1519.2"/>
<text xml:space="preserve" text-anchor="start" x="498.71" y="-1441.2" font-family="Arial" font-size="20.00" fill="#eff6ff">UploadReportOnlyPreflightHook</text>
<text xml:space="preserve" text-anchor="start" x="515.09" y="-1418.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">Report&#45;only hook: disabled by default,</text>
<text xml:space="preserve" text-anchor="start" x="565.13" y="-1400.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">fail&#45;open, never rejects</text>
</g>
<!-- tikaprovider -->
<g id="node2" class="node">
<title>tikaprovider</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-1196.4 47.98,-1196.4 47.98,-1016.4 368.02,-1016.4 368.02,-1196.4"/>
<text xml:space="preserve" text-anchor="start" x="114.08" y="-1118.4" font-family="Arial" font-size="20.00" fill="#eff6ff">TikaDetectorProvider</text>
<text xml:space="preserve" text-anchor="start" x="83.37" y="-1095.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">MIME detection, extension mismatch,</text>
<text xml:space="preserve" text-anchor="start" x="134.2" y="-1077.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">content&#45;type detection</text>
</g>
<!-- ffprobeprovider -->
<g id="node3" class="node">
<title>ffprobeprovider</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="804.06,-1196.4 477.94,-1196.4 477.94,-1016.4 804.06,-1016.4 804.06,-1196.4"/>
<text xml:space="preserve" text-anchor="start" x="524.28" y="-1118.4" font-family="Arial" font-size="20.00" fill="#eff6ff">FFprobeMetadataProvider</text>
<text xml:space="preserve" text-anchor="start" x="498" y="-1095.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">Media technical metadata: duration, codec,</text>
<text xml:space="preserve" text-anchor="start" x="608.48" y="-1077.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">resolution</text>
</g>
<!-- metadatamerger -->
<g id="node4" class="node">
<title>metadatamerger</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1234.02,-1196.4 913.98,-1196.4 913.98,-1016.4 1234.02,-1016.4 1234.02,-1196.4"/>
<text xml:space="preserve" text-anchor="start" x="973.39" y="-1109.4" font-family="Arial" font-size="20.00" fill="#eff6ff">IngestMetadataMerger</text>
<text xml:space="preserve" text-anchor="start" x="937.06" y="-1086.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">Merges Tika + FFprobe into unified result</text>
</g>
<!-- safereportdto -->
<g id="node5" class="node">
<title>safereportdto</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1231.02,-873.6 910.98,-873.6 910.98,-693.6 1231.02,-693.6 1231.02,-873.6"/>
<text xml:space="preserve" text-anchor="start" x="940.95" y="-795.6" font-family="Arial" font-size="20.00" fill="#eff6ff">SafePreflightReportSummary</text>
<text xml:space="preserve" text-anchor="start" x="938.43" y="-772.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Safe internal DTO: no raw metadata, no</text>
<text xml:space="preserve" text-anchor="start" x="1015.55" y="-754.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">storage internals</text>
</g>
<!-- policyevaluator -->
<g id="node6" class="node">
<title>policyevaluator</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1225.24,-550.8 872.76,-550.8 872.76,-370.8 1225.24,-370.8 1225.24,-550.8"/>
<text xml:space="preserve" text-anchor="start" x="892.82" y="-472.8" font-family="Arial" font-size="20.00" fill="#eff6ff">ReportOnlyPreflightPolicyEvaluator</text>
<text xml:space="preserve" text-anchor="start" x="912.69" y="-449.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Report&#45;only evaluator: never rejects, fails</text>
<text xml:space="preserve" text-anchor="start" x="1032.32" y="-431.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">open</text>
</g>
<!-- policyresult -->
<g id="node7" class="node">
<title>policyresult</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1234.5,-228 833.5,-228 833.5,-48 1234.5,-48 1234.5,-228"/>
<text xml:space="preserve" text-anchor="start" x="895.6" y="-150" font-family="Arial" font-size="20.00" fill="#eff6ff">PreflightPolicyEvaluationResult</text>
<text xml:space="preserve" text-anchor="start" x="853.56" y="-127" font-family="Arial" font-size="15.00" fill="#bfdbfe">Policy result: ACCEPT, ACCEPT_WITH_WARNINGS,</text>
<text xml:space="preserve" text-anchor="start" x="957.32" y="-109" font-family="Arial" font-size="15.00" fill="#bfdbfe">REJECT_CANDIDATE</text>
</g>
<!-- uploadhook&#45;&gt;tikaprovider -->
<g id="edge1" class="edge">
<title>uploadhook&#45;&gt;tikaprovider</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M520.96,-1339.27C463.47,-1296.67 394.58,-1245.63 336.08,-1202.29"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="337.92,-1200.39 330.33,-1198.03 334.79,-1204.61 337.92,-1200.39"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="437.48,-1256.4 437.48,-1279.2 529.06,-1279.2 529.06,-1256.4 437.48,-1256.4"/>
<text xml:space="preserve" text-anchor="start" x="440.48" y="-1262.2" font-family="Arial" font-size="14.00" fill="#c9c9c9">detects MIME</text>
</g>
<!-- uploadhook&#45;&gt;ffprobeprovider -->
<g id="edge2" class="edge">
<title>uploadhook&#45;&gt;ffprobeprovider</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M641,-1339.27C641,-1298.07 641,-1248.96 641,-1206.57"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="643.63,-1206.76 641,-1199.26 638.38,-1206.76 643.63,-1206.76"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="641,-1256.4 641,-1279.2 731.83,-1279.2 731.83,-1256.4 641,-1256.4"/>
<text xml:space="preserve" text-anchor="start" x="644" y="-1262.2" font-family="Arial" font-size="14.00" fill="#c9c9c9">probes media</text>
</g>
<!-- uploadhook&#45;&gt;metadatamerger -->
<g id="edge3" class="edge">
<title>uploadhook&#45;&gt;metadatamerger</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M761.04,-1339.27C818.53,-1296.67 887.42,-1245.63 945.92,-1202.29"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="947.21,-1204.61 951.67,-1198.03 944.08,-1200.39 947.21,-1204.61"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="870.48,-1256.4 870.48,-1279.2 968.29,-1279.2 968.29,-1256.4 870.48,-1256.4"/>
<text xml:space="preserve" text-anchor="start" x="873.48" y="-1262.2" font-family="Arial" font-size="14.00" fill="#c9c9c9">merges results</text>
</g>
<!-- metadatamerger&#45;&gt;safereportdto -->
<g id="edge4" class="edge">
<title>metadatamerger&#45;&gt;safereportdto</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1073.17,-1016.47C1072.78,-975.27 1072.32,-926.16 1071.93,-883.77"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1074.55,-883.93 1071.86,-876.46 1069.3,-883.98 1074.55,-883.93"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1072.59,-933.6 1072.59,-956.4 1207,-956.4 1207,-933.6 1072.59,-933.6"/>
<text xml:space="preserve" text-anchor="start" x="1075.59" y="-939.4" font-family="Arial" font-size="14.00" fill="#c9c9c9">produces safe report</text>
</g>
<!-- safereportdto&#45;&gt;policyevaluator -->
<g id="edge5" class="edge">
<title>safereportdto&#45;&gt;policyevaluator</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1064.9,-693.67C1062.08,-652.47 1058.71,-603.36 1055.8,-560.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1058.43,-560.95 1055.3,-553.65 1053.19,-561.31 1058.43,-560.95"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1060.66,-610.8 1060.66,-633.6 1166.27,-633.6 1166.27,-610.8 1060.66,-610.8"/>
<text xml:space="preserve" text-anchor="start" x="1063.66" y="-616.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">evaluates policy</text>
</g>
<!-- policyevaluator&#45;&gt;policyresult -->
<g id="edge6" class="edge">
<title>policyevaluator&#45;&gt;policyresult</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1044.84,-370.87C1042.92,-329.67 1040.62,-280.56 1038.64,-238.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1041.27,-238.22 1038.29,-230.85 1036.02,-238.47 1041.27,-238.22"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1041.95,-288 1041.95,-310.8 1143.67,-310.8 1143.67,-288 1041.95,-288"/>
<text xml:space="preserve" text-anchor="start" x="1044.95" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">produces result</text>
</g>
</g>
</svg>
`;case`r2ArtifactAccessPath`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="847pt" height="533pt"
 viewBox="0.00 0.00 847.00 533.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 517.85)">
<!-- s3materializer -->
<g id="node1" class="node">
<title>s3materializer</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="324.71,-502.8 4.67,-502.8 4.67,-322.8 324.71,-322.8 324.71,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="71.87" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">S3ObjectMaterializer</text>
<text xml:space="preserve" text-anchor="start" x="37.13" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">S3/R2&#45;compatible storage materializer</text>
</g>
<!-- accessdescriptor -->
<g id="node2" class="node">
<title>accessdescriptor</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="329.37,-180 0,-180 0,0 329.37,0 329.37,-180"/>
<text xml:space="preserve" text-anchor="start" x="86.89" y="-102" font-family="Arial" font-size="20.00" fill="#eff6ff">AccessDescriptor</text>
<text xml:space="preserve" text-anchor="start" x="20.06" y="-79" font-family="Arial" font-size="15.00" fill="#bfdbfe">User&#45;facing access contract: SIGNED_URL</text>
<text xml:space="preserve" text-anchor="start" x="91.3" y="-61" font-family="Arial" font-size="15.00" fill="#bfdbfe">generated on demand</text>
</g>
<!-- storage -->
<g id="node3" class="node">
<title>storage</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="787.71,-180 467.67,-180 467.67,0 787.71,0 787.71,-180"/>
<text xml:space="preserve" text-anchor="start" x="592.66" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage</text>
<text xml:space="preserve" text-anchor="start" x="514.3" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Object storage / shared filesystem</text>
</g>
<!-- storageruntime -->
<g id="node4" class="node">
<title>storageruntime</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="816.71,-502.8 496.67,-502.8 496.67,-322.8 816.71,-322.8 816.71,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="581.65" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage Runtime</text>
<text xml:space="preserve" text-anchor="start" x="534.12" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Storage/materialization management</text>
</g>
<!-- s3materializer&#45;&gt;accessdescriptor -->
<g id="edge1" class="edge">
<title>s3materializer&#45;&gt;accessdescriptor</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M149.18,-323.01C146.35,-303.25 143.83,-282.36 142.38,-262.8 140.63,-239.29 142.04,-214.06 144.85,-190.38"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="147.44,-190.75 145.79,-182.98 142.24,-190.09 147.44,-190.75"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="142.38,-240 142.38,-262.8 287.69,-262.8 287.69,-240 142.38,-240"/>
<text xml:space="preserve" text-anchor="start" x="145.38" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">generates signed URL</text>
</g>
<!-- s3materializer&#45;&gt;storage -->
<g id="edge2" class="edge">
<title>s3materializer&#45;&gt;storage</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M293.04,-322.87C354.64,-280.19 428.49,-229.02 491.12,-185.63"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="492.32,-187.98 496.99,-181.55 489.33,-183.67 492.32,-187.98"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="410.07,-240 410.07,-262.8 527.35,-262.8 527.35,-240 410.07,-240"/>
<text xml:space="preserve" text-anchor="start" x="413.07" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">R2/S3&#45;compatible</text>
</g>
<!-- storageruntime&#45;&gt;storage -->
<g id="edge3" class="edge">
<title>storageruntime&#45;&gt;storage</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M648.65,-322.87C644.92,-281.67 640.48,-232.56 636.65,-190.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="639.28,-190.08 635.99,-182.84 634.05,-190.55 639.28,-190.08"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="643.06,-240 643.06,-262.8 750.19,-262.8 750.19,-240 643.06,-240"/>
<text xml:space="preserve" text-anchor="start" x="646.06" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">persists artifacts</text>
</g>
</g>
</svg>
`;default:throw Error(`Unknown viewId: `+e)}};export{e as dotSource,t as svgSource};