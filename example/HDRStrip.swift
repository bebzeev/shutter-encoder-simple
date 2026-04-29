import Cocoa

// MARK: - App Delegate

class AppDelegate: NSObject, NSApplicationDelegate {
    var window: NSWindow!

    func applicationDidFinishLaunching(_ notification: Notification) {
        let frame = NSRect(x: 0, y: 0, width: 520, height: 460)
        window = NSWindow(
            contentRect: frame,
            styleMask: [.titled, .closable, .miniaturizable],
            backing: .buffered,
            defer: false
        )
        window.title = "HDR Strip"
        window.center()
        window.contentView = MainView(frame: frame)
        window.makeKeyAndOrderFront(nil)
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        return true
    }

    func application(_ sender: NSApplication, openFiles filenames: [String]) {
        guard let mainView = window.contentView as? MainView else { return }
        let urls = filenames.compactMap { URL(fileURLWithPath: $0) }
        let videoURLs = urls.filter { mainView.allowedExtensions.contains($0.pathExtension.lowercased()) }
        if !videoURLs.isEmpty {
            mainView.addVideos(videoURLs)
        }
    }
}

// MARK: - Video Item

class VideoItem {
    let url: URL
    var progress: Double = 0
    var status: String = "Waiting..."
    var isDone: Bool = false
    var isError: Bool = false

    var outputURL: URL {
        let dir = url.deletingLastPathComponent()
        let name = url.deletingPathExtension().lastPathComponent
        return dir.appendingPathComponent("\(name)_SDR.mov")
    }

    init(url: URL) {
        self.url = url
    }
}

// MARK: - Drop Zone View

class DropZoneView: NSView {
    var onDrop: (([URL]) -> Void)?
    let allowedExtensions = ["mov", "mp4", "m4v"]
    private var isDragOver = false

    override init(frame: NSRect) {
        super.init(frame: frame)
        registerForDraggedTypes([.fileURL])
        wantsLayer = true
        layer?.cornerRadius = 12
        updateAppearance()
    }

    required init?(coder: NSCoder) { fatalError() }

    private func updateAppearance() {
        layer?.backgroundColor = isDragOver
            ? NSColor.controlAccentColor.withAlphaComponent(0.15).cgColor
            : NSColor.windowBackgroundColor.cgColor
        layer?.borderColor = isDragOver
            ? NSColor.controlAccentColor.cgColor
            : NSColor.separatorColor.cgColor
        layer?.borderWidth = 2
        needsDisplay = true
    }

    override func draw(_ dirtyRect: NSRect) {
        super.draw(dirtyRect)
        let style = NSMutableParagraphStyle()
        style.alignment = .center

        let iconAttrs: [NSAttributedString.Key: Any] = [
            .font: NSFont.systemFont(ofSize: 36),
            .foregroundColor: NSColor.tertiaryLabelColor,
            .paragraphStyle: style
        ]
        let icon = NSAttributedString(string: "\u{1F3AC}", attributes: iconAttrs)
        let iconRect = NSRect(x: 0, y: bounds.midY + 5, width: bounds.width, height: 50)
        icon.draw(in: iconRect)

        let textAttrs: [NSAttributedString.Key: Any] = [
            .font: NSFont.systemFont(ofSize: 15, weight: .medium),
            .foregroundColor: NSColor.secondaryLabelColor,
            .paragraphStyle: style
        ]
        let text = NSAttributedString(string: "Drop HDR Videos Here", attributes: textAttrs)
        let textRect = NSRect(x: 0, y: bounds.midY - 25, width: bounds.width, height: 30)
        text.draw(in: textRect)

        let subAttrs: [NSAttributedString.Key: Any] = [
            .font: NSFont.systemFont(ofSize: 11),
            .foregroundColor: NSColor.tertiaryLabelColor,
            .paragraphStyle: style
        ]
        let sub = NSAttributedString(string: ".mov  .mp4  .m4v", attributes: subAttrs)
        let subRect = NSRect(x: 0, y: bounds.midY - 48, width: bounds.width, height: 20)
        sub.draw(in: subRect)
    }

    private func hasValidFiles(_ info: NSDraggingInfo) -> Bool {
        guard let items = info.draggingPasteboard.pasteboardItems else { return false }
        for item in items {
            if let urlString = item.string(forType: .fileURL),
               let url = URL(string: urlString) {
                if allowedExtensions.contains(url.pathExtension.lowercased()) {
                    return true
                }
            }
        }
        return false
    }

    override func draggingEntered(_ sender: NSDraggingInfo) -> NSDragOperation {
        if hasValidFiles(sender) {
            isDragOver = true
            updateAppearance()
            return .copy
        }
        return []
    }

    override func draggingExited(_ sender: NSDraggingInfo?) {
        isDragOver = false
        updateAppearance()
    }

    override func performDragOperation(_ sender: NSDraggingInfo) -> Bool {
        isDragOver = false
        updateAppearance()
        guard let items = sender.draggingPasteboard.pasteboardItems else { return false }
        var urls: [URL] = []
        for item in items {
            if let urlString = item.string(forType: .fileURL),
               let url = URL(string: urlString) {
                if allowedExtensions.contains(url.pathExtension.lowercased()) {
                    urls.append(url)
                }
            }
        }
        if !urls.isEmpty {
            onDrop?(urls)
            return true
        }
        return false
    }
}

// MARK: - Video Row View

class VideoRowView: NSView {
    let nameLabel = NSTextField(labelWithString: "")
    let statusLabel = NSTextField(labelWithString: "Waiting...")
    let progressBar = NSProgressIndicator()

    init(item: VideoItem) {
        super.init(frame: .zero)

        nameLabel.font = .systemFont(ofSize: 12, weight: .medium)
        nameLabel.lineBreakMode = .byTruncatingMiddle
        nameLabel.stringValue = item.url.lastPathComponent

        statusLabel.font = .systemFont(ofSize: 11)
        statusLabel.textColor = .secondaryLabelColor

        progressBar.isIndeterminate = false
        progressBar.minValue = 0
        progressBar.maxValue = 100
        progressBar.doubleValue = 0
        progressBar.style = .bar
        progressBar.controlSize = .small

        for v in [nameLabel, statusLabel, progressBar] {
            v.translatesAutoresizingMaskIntoConstraints = false
            addSubview(v)
        }

        NSLayoutConstraint.activate([
            nameLabel.topAnchor.constraint(equalTo: topAnchor, constant: 6),
            nameLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            nameLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),

            progressBar.topAnchor.constraint(equalTo: nameLabel.bottomAnchor, constant: 4),
            progressBar.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            progressBar.trailingAnchor.constraint(equalTo: statusLabel.leadingAnchor, constant: -8),

            statusLabel.centerYAnchor.constraint(equalTo: progressBar.centerYAnchor),
            statusLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            statusLabel.widthAnchor.constraint(equalToConstant: 90),

            bottomAnchor.constraint(equalTo: progressBar.bottomAnchor, constant: 8)
        ])
    }

    required init?(coder: NSCoder) { fatalError() }

    func update(progress: Double, status: String, isDone: Bool, isError: Bool) {
        progressBar.doubleValue = progress
        statusLabel.stringValue = status
        if isDone {
            statusLabel.textColor = .systemGreen
        } else if isError {
            statusLabel.textColor = .systemRed
        } else if status == "Converting..." {
            statusLabel.textColor = .controlAccentColor
        } else {
            statusLabel.textColor = .secondaryLabelColor
        }
    }
}

// MARK: - Main View

class MainView: NSView {
    let allowedExtensions = ["mov", "mp4", "m4v"]
    private var dropZone: DropZoneView!
    private var scrollView: NSScrollView!
    private var stackView: NSStackView!
    private var statusLabel: NSTextField!
    private var items: [VideoItem] = []
    private var rowViews: [VideoRowView] = []
    private var isConverting = false

    override init(frame: NSRect) {
        super.init(frame: frame)
        setupUI()
    }

    required init?(coder: NSCoder) { fatalError() }

    private func setupUI() {
        dropZone = DropZoneView(frame: .zero)
        dropZone.translatesAutoresizingMaskIntoConstraints = false
        dropZone.onDrop = { [weak self] urls in self?.addVideos(urls) }
        addSubview(dropZone)

        stackView = NSStackView()
        stackView.orientation = .vertical
        stackView.alignment = .leading
        stackView.spacing = 2
        stackView.translatesAutoresizingMaskIntoConstraints = false

        scrollView = NSScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.documentView = stackView
        scrollView.hasVerticalScroller = true
        scrollView.drawsBackground = false
        scrollView.isHidden = true
        addSubview(scrollView)

        statusLabel = NSTextField(labelWithString: "")
        statusLabel.font = .systemFont(ofSize: 12)
        statusLabel.textColor = .secondaryLabelColor
        statusLabel.alignment = .center
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        addSubview(statusLabel)

        NSLayoutConstraint.activate([
            dropZone.topAnchor.constraint(equalTo: topAnchor, constant: 16),
            dropZone.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            dropZone.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            dropZone.heightAnchor.constraint(equalToConstant: 140),

            scrollView.topAnchor.constraint(equalTo: dropZone.bottomAnchor, constant: 12),
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            scrollView.bottomAnchor.constraint(equalTo: statusLabel.topAnchor, constant: -8),

            stackView.widthAnchor.constraint(equalTo: scrollView.widthAnchor),

            statusLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            statusLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            statusLabel.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -12),
            statusLabel.heightAnchor.constraint(equalToConstant: 20),
        ])
    }

    func addVideos(_ urls: [URL]) {
        scrollView.isHidden = false
        for url in urls {
            if items.contains(where: { $0.url == url }) { continue }
            let item = VideoItem(url: url)
            items.append(item)
            let row = VideoRowView(item: item)
            rowViews.append(row)
            stackView.addArrangedSubview(row)
            row.translatesAutoresizingMaskIntoConstraints = false
            row.widthAnchor.constraint(equalTo: stackView.widthAnchor).isActive = true
        }
        updateStatus()
        if !isConverting {
            processNext()
        }
    }

    private func updateStatus() {
        let done = items.filter { $0.isDone }.count
        let errors = items.filter { $0.isError }.count
        let total = items.count
        if done + errors == total && total > 0 {
            if errors > 0 {
                statusLabel.stringValue = "Done: \(done) converted, \(errors) failed"
            } else {
                statusLabel.stringValue = "All \(done) video\(done == 1 ? "" : "s") converted!"
            }
            statusLabel.textColor = errors > 0 ? .systemOrange : .systemGreen
            isConverting = false
        } else {
            let remaining = total - done - errors
            statusLabel.stringValue = "\(remaining) remaining, \(done) done"
            statusLabel.textColor = .secondaryLabelColor
        }
    }

    private func processNext() {
        guard let index = items.firstIndex(where: { !$0.isDone && !$0.isError && $0.status == "Waiting..." }) else {
            updateStatus()
            return
        }
        isConverting = true
        let item = items[index]
        let row = rowViews[index]
        item.status = "Converting..."
        DispatchQueue.main.async {
            row.update(progress: 0, status: "Converting...", isDone: false, isError: false)
            self.updateStatus()
        }

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.runConversion(item: item, row: row) {
                DispatchQueue.main.async {
                    self?.processNext()
                }
            }
        }
    }

    private func runConversion(item: VideoItem, row: VideoRowView, completion: @escaping () -> Void) {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/avconvert")
        process.arguments = [
            "-s", item.url.path,
            "-p", "PresetHighestQuality",
            "-o", item.outputURL.path,
            "--replace",
            "--progress"
        ]

        let pipe = Pipe()
        process.standardOutput = pipe
        process.standardError = pipe

        let handle = pipe.fileHandleForReading
        handle.readabilityHandler = { fh in
            let data = fh.availableData
            guard !data.isEmpty, let output = String(data: data, encoding: .utf8) else { return }
            // Parse lines like "avconvert progress:  45.23% complete"
            let lines = output.components(separatedBy: CharacterSet.newlines)
            for line in lines {
                if line.contains("progress:") {
                    let parts = line.components(separatedBy: "progress:")
                    if parts.count > 1 {
                        let pctStr = parts[1]
                            .replacingOccurrences(of: "% complete", with: "")
                            .trimmingCharacters(in: .whitespaces)
                        if let pct = Double(pctStr) {
                            DispatchQueue.main.async {
                                item.progress = pct
                                row.update(progress: pct, status: "Converting...", isDone: false, isError: false)
                            }
                        }
                    }
                }
            }
        }

        do {
            try process.run()
            process.waitUntilExit()
        } catch {
            DispatchQueue.main.async {
                item.isError = true
                item.status = "Error"
                row.update(progress: 0, status: "Error", isDone: false, isError: true)
            }
            handle.readabilityHandler = nil
            completion()
            return
        }

        handle.readabilityHandler = nil

        DispatchQueue.main.async {
            if process.terminationStatus == 0 {
                item.isDone = true
                item.progress = 100
                item.status = "Done"
                row.update(progress: 100, status: "Done", isDone: true, isError: false)
            } else {
                item.isError = true
                item.status = "Error"
                row.update(progress: item.progress, status: "Error", isDone: false, isError: true)
            }
            self.updateStatus()
        }
        completion()
    }
}

// MARK: - Main Entry

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.regular)
app.activate(ignoringOtherApps: true)
app.run()
